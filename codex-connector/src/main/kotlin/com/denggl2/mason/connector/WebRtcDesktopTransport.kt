package com.denggl2.mason.connector

import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingResult
import com.denggl2.mason.protocol.PairingRoute
import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCAnswerOptions
import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCDataChannel
import dev.onvoid.webrtc.RTCDataChannelBuffer
import dev.onvoid.webrtc.RTCDataChannelInit
import dev.onvoid.webrtc.RTCDataChannelObserver
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCIceConnectionState
import dev.onvoid.webrtc.RTCIceGatheringState
import dev.onvoid.webrtc.RTCIceServer
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCPeerConnectionState
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * Desktop-side WebRTC transport.
 *
 * The signaling service only carries short-lived SDP/ICE state. Once the data
 * channel is open, every business request is forwarded to the loopback HTTP
 * service and no business payload is sent to the signaling service.
 */
internal class WebRtcDesktopTransport(
    signalingEndpoint: String,
    private val offerId: String,
    private val expiresAt: Long,
    private val route: PairingRoute,
    private val loopbackEndpoint: URI,
    private val resumeOfferIdForPairing: String? = null,
    private val deleteAfterPairing: Boolean = true,
    private val onSessionClosed: (() -> Unit)? = null,
) : AutoCloseable {
    private val signalingEndpoint = URI.create(signalingEndpoint.trimEnd('/') + "/")
    private val signalingClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
    private val localClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peerRef = AtomicReference<RTCPeerConnection?>()
    private val factoryRef = AtomicReference<PeerConnectionFactory?>()
    private val channelRef = AtomicReference<RTCDataChannel?>()
    private val channelOpen = CompletableDeferred<Unit>()
    private val gatheringComplete = CompletableDeferred<Unit>()
    private val remoteDescriptionSet = CompletableDeferred<Unit>()
    private val localCandidates = CopyOnWriteArrayList<RTCIceCandidate>()
    private val remoteCandidateKeys = HashSet<String>()
    private var appliedAnswerRevision = -1L
    private val signalingReady = AtomicBoolean(false)
    private val signalingDeleted = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val sessionClosedNotified = AtomicBoolean(false)
    private val channelOpened = AtomicBoolean(false)
    private val channelLock = Any()

    fun start() {
        check(!closed.get()) { "WebRTC desktop transport is closed" }
        runBlocking(Dispatchers.IO) {
            val factory = PeerConnectionFactory()
            factoryRef.set(factory)
            val peer = factory.createPeerConnection(
                buildConfiguration(),
                object : PeerConnectionObserver {
                    override fun onIceCandidate(candidate: RTCIceCandidate) {
                        localCandidates += candidate
                        if (signalingReady.get()) {
                            scope.launch { postCandidate("desktop", candidate) }
                        }
                    }

                    override fun onIceGatheringChange(state: RTCIceGatheringState) {
                        if (state == RTCIceGatheringState.COMPLETE) {
                            gatheringComplete.complete(Unit)
                        }
                    }

                    override fun onDataChannel(channel: RTCDataChannel) {
                        registerChannel(channel)
                    }

                    override fun onIceConnectionChange(state: RTCIceConnectionState) {
                        if (state == RTCIceConnectionState.FAILED) {
                            failSession("WebRTC ICE 连接失败")
                        }
                    }

                    override fun onConnectionChange(state: RTCPeerConnectionState) {
                        if (state == RTCPeerConnectionState.FAILED ||
                            state == RTCPeerConnectionState.CLOSED
                        ) {
                            failSession("WebRTC PeerConnection 连接失败")
                        }
                    }
                },
            ) ?: error("无法创建 WebRTC PeerConnection")
            peerRef.set(peer)

            try {
                val channel = peer.createDataChannel("mason-rpc", RTCDataChannelInit().apply {
                    ordered = true
                }) ?: error("无法创建 WebRTC DataChannel")
                registerChannel(channel)

                val offer = createOffer(peer)
                setDescription(peer, local = true, description = offer)
                if (peer.iceGatheringState == RTCIceGatheringState.COMPLETE) {
                    gatheringComplete.complete(Unit)
                }
                // Some native WebRTC builds emit candidates but never deliver the
                // ICE gathering COMPLETE callback. The local SDP and candidate
                // callbacks are still usable, so publish them after the bounded wait.
                withTimeoutOrNull(15_000) { gatheringComplete.await() }

                val localDescription = peer.localDescription ?: offer
                postDescription(localDescription)
                signalingReady.set(true)
                localCandidates.forEach { candidate ->
                    postCandidate("desktop", candidate)
                }
                scope.launch { pollRemoteDescriptionAndCandidates() }
                scope.launch {
                    val refreshDelay = (expiresAt - System.currentTimeMillis() - OFFER_REFRESH_MARGIN_MILLIS)
                        .coerceAtLeast(1_000L)
                    delay(refreshDelay)
                    if (!closed.get() && !channelOpened.get()) {
                        notifySessionClosed()
                        close()
                    }
                }
            } catch (error: Throwable) {
                close()
                throw error
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        channelRef.getAndSet(null)?.let { channel ->
            runCatching { channel.unregisterObserver() }
            runCatching { channel.close() }
            runCatching { channel.dispose() }
        }
        peerRef.getAndSet(null)?.let { peer ->
            runCatching { peer.close() }
        }
        factoryRef.getAndSet(null)?.let { factory ->
            runCatching { factory.dispose() }
        }
    }

    private fun registerChannel(channel: RTCDataChannel) {
        synchronized(channelLock) {
            if (closed.get()) {
                channel.dispose()
                return
            }
            if (!channelRef.compareAndSet(null, channel)) {
                channel.dispose()
                return
            }
            channel.registerObserver(object : RTCDataChannelObserver {
                override fun onBufferedAmountChange(previousAmount: Long) = Unit

                override fun onStateChange() {
                    when (channel.state) {
                        dev.onvoid.webrtc.RTCDataChannelState.OPEN -> {
                            channelOpened.set(true)
                            channelOpen.complete(Unit)
                        }

                        dev.onvoid.webrtc.RTCDataChannelState.CLOSING,
                        dev.onvoid.webrtc.RTCDataChannelState.CLOSED,
                        -> failSession("WebRTC DataChannel 已关闭")

                        else -> Unit
                    }
                }

                override fun onMessage(buffer: RTCDataChannelBuffer) {
                    val bytes = ByteArray(buffer.data.remaining()).also(buffer.data::get)
                    scope.launch { handleRpc(bytes) }
                }
            })
            if (channel.state == dev.onvoid.webrtc.RTCDataChannelState.OPEN) {
                channelOpened.set(true)
                channelOpen.complete(Unit)
            }
        }
    }

    private suspend fun handleRpc(bytes: ByteArray) {
        val request = runCatching {
            MasonProtocolJson.decode<DesktopRpcRequest>(String(bytes, Charsets.UTF_8))
        }.getOrElse { return }
        val response = runCatching { forwardToLoopback(request) }
            .getOrElse { error ->
                DesktopRpcResponse(
                    requestId = request.requestId,
                    status = 502,
                    bodyBase64 = Base64.getEncoder().encodeToString(
                        MasonProtocolJson.encode(
                            com.denggl2.mason.protocol.ProtocolErrorResponse(
                                code = "WEBRTC_LOOPBACK_FAILED",
                                message = error.message ?: "桌面端本地服务不可用",
                            ),
                        ).toByteArray(Charsets.UTF_8),
                    ),
                )
            }
        if (deleteAfterPairing &&
            request.path == "/v1/pairing/complete" &&
            response.status in 200..299 &&
            signalingDeleted.compareAndSet(false, true)
        ) {
            scope.launch { deleteOffer() }
        }
        val channel = channelRef.get() ?: return
        val payload = MasonProtocolJson.encode(response).toByteArray(Charsets.UTF_8)
        runCatching { channel.send(RTCDataChannelBuffer(ByteBuffer.wrap(payload), false)) }
    }

    private suspend fun forwardToLoopback(request: DesktopRpcRequest): DesktopRpcResponse =
        withContext(Dispatchers.IO) {
            require(request.path.startsWith('/')) { "WebRTC RPC path is invalid" }
            val target = loopbackEndpoint.resolve(request.path.removePrefix("/"))
            val builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(65))
            request.headers.forEach { (name, value) ->
                if (!name.equals("Host", ignoreCase = true) &&
                    !name.equals("Content-Length", ignoreCase = true)
                ) {
                    runCatching { builder.header(name, value) }
                }
            }
            val body = request.bodyBase64?.let(Base64.getDecoder()::decode)
            when {
                body != null -> builder.method(
                    request.method,
                    HttpRequest.BodyPublishers.ofByteArray(body),
                )

                request.method.equals("GET", ignoreCase = true) -> builder.GET()
                request.method.equals("HEAD", ignoreCase = true) -> builder.method(
                    "HEAD",
                    HttpRequest.BodyPublishers.noBody(),
                )

                else -> builder.method(request.method, HttpRequest.BodyPublishers.noBody())
            }
            val response = localClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
            DesktopRpcResponse(
                requestId = request.requestId,
                status = response.statusCode(),
                headers = response.headers().map().mapValues { (_, values) -> values.joinToString(",") },
                bodyBase64 = response.body()
                    .takeIf { it.isNotEmpty() }
                    ?.let(Base64.getEncoder()::encodeToString),
            ).let(::addResumeOfferToPairingResponse)
        }

    private fun addResumeOfferToPairingResponse(response: DesktopRpcResponse): DesktopRpcResponse {
        val resumeOfferId = resumeOfferIdForPairing?.takeIf(String::isNotBlank)
            ?: return response
        if (response.status !in 200..299 || response.bodyBase64 == null) return response
        val pairingResult = runCatching {
            MasonProtocolJson.decode<PairingResult>(
                String(Base64.getDecoder().decode(response.bodyBase64), Charsets.UTF_8),
            )
        }.getOrNull() ?: return response
        return response.copy(
            bodyBase64 = Base64.getEncoder().encodeToString(
                MasonProtocolJson.encode(pairingResult.copy(resumeOfferId = resumeOfferId))
                    .toByteArray(Charsets.UTF_8),
            ),
        )
    }

    private fun failSession(message: String) {
        if (closed.get()) return
        channelOpen.completeExceptionally(IllegalStateException(message))
        notifySessionClosed()
    }

    private fun notifySessionClosed() {
        if (sessionClosedNotified.compareAndSet(false, true)) {
            onSessionClosed?.invoke()
        }
    }

    private suspend fun pollRemoteDescriptionAndCandidates() {
        while (scope.isActive && !closed.get()) {
            runCatching { fetchAnswer() }
            if (remoteDescriptionSet.isCompleted) {
                runCatching { fetchRemoteCandidates() }
            }
            if (channelOpen.isCompleted) return
            delay(250)
        }
    }

    private suspend fun fetchAnswer() {
        val response = signalingRequest("GET", "v1/offers/$offerId/answer")
        if (response.statusCode == 404) return
        check(response.statusCode in 200..299) { "WebRTC answer 获取失败（${response.statusCode}）" }
        val payload = MasonProtocolJson.decode<SignalingDescription>(response.body)
        if (remoteDescriptionSet.isCompleted) {
            if (payload.revision <= appliedAnswerRevision) return
            notifySessionClosed()
            close()
            return
        }
        val peer = requireNotNull(peerRef.get())
        setDescription(
            peer,
            local = false,
            description = RTCSessionDescription(
                RTCSdpType.ANSWER,
                payload.sdp,
            ),
        )
        appliedAnswerRevision = payload.revision
        remoteDescriptionSet.complete(Unit)
    }

    private suspend fun fetchRemoteCandidates() {
        val response = signalingRequest("GET", "v1/offers/$offerId/candidates?side=desktop")
        if (response.statusCode == 404) return
        check(response.statusCode in 200..299) { "WebRTC ICE 获取失败（${response.statusCode}）" }
        val payload = MasonProtocolJson.decode<SignalingCandidates>(response.body)
        val peer = requireNotNull(peerRef.get())
        payload.candidates.forEach { candidate ->
            val key = candidateKey(candidate)
            synchronized(remoteCandidateKeys) {
                if (!remoteCandidateKeys.add(key)) return@forEach
            }
            peer.addIceCandidate(
                RTCIceCandidate(
                    candidate.sdpMid,
                    candidate.sdpMLineIndex,
                    candidate.candidate,
                ),
            )
        }
    }

    private fun postDescription(description: RTCSessionDescription) {
        val payload = MasonProtocolJson.encode(
            SignalingDescriptionUpload(
                offerId = offerId,
                type = description.sdpType.name.lowercase(),
                sdp = description.sdp,
                expiresAt = expiresAt,
            ),
        )
        val response = signalingRequestBlocking("POST", "v1/offers/$offerId", payload)
        check(response.statusCode in 200..299) { "WebRTC offer 登记失败（${response.statusCode}）" }
    }

    private fun postCandidate(side: String, candidate: RTCIceCandidate) {
        val payload = MasonProtocolJson.encode(
            SignalingCandidateUpload(
                side = side,
                candidate = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex,
            ),
        )
        val response = signalingRequestBlocking("POST", "v1/offers/$offerId/candidates", payload)
        check(response.statusCode in 200..299) { "WebRTC ICE 登记失败（${response.statusCode}）" }
    }

    private fun deleteOffer() {
        runCatching { signalingRequestBlocking("DELETE", "v1/offers/$offerId", null) }
    }

    private suspend fun signalingRequest(method: String, path: String): SignalingResponse =
        withContext(Dispatchers.IO) { signalingRequestBlocking(method, path, null) }

    private fun signalingRequestBlocking(
        method: String,
        path: String,
        body: String?,
    ): SignalingResponse {
        val builder = HttpRequest.newBuilder(signalingEndpoint.resolve(path))
            .timeout(Duration.ofSeconds(15))
        if (body != null) {
            builder.header("Content-Type", "application/json")
        }
        when {
            body != null -> builder.method(method, HttpRequest.BodyPublishers.ofString(body))
            method == "GET" -> builder.GET()
            method == "DELETE" -> builder.DELETE()
            else -> builder.method(method, HttpRequest.BodyPublishers.noBody())
        }
        val response = signalingClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return SignalingResponse(response.statusCode(), response.body())
    }

    private fun buildConfiguration(): RTCConfiguration = RTCConfiguration().apply {
        iceServers = buildList {
            route.stunServers.forEach { url ->
                add(RTCIceServer().apply { urls = listOf(url) })
            }
            route.turnServers.forEach { server ->
                if (server.urls.isNotEmpty()) {
                    add(RTCIceServer().apply {
                        urls = server.urls
                        username = server.username.orEmpty()
                        password = server.credential.orEmpty()
                    })
                }
            }
        }
    }

    private fun createOffer(peer: RTCPeerConnection): RTCSessionDescription =
        runBlocking(Dispatchers.IO) {
            withTimeout(15_000) {
                CompletableDeferred<RTCSessionDescription>().also { result ->
                    peer.createOffer(RTCOfferOptions(), object : CreateSessionDescriptionObserver {
                        override fun onSuccess(description: RTCSessionDescription) {
                            result.complete(description)
                        }

                        override fun onFailure(error: String) {
                            result.completeExceptionally(IllegalStateException(error))
                        }
                    })
                }.await()
            }
        }

    private fun setDescription(
        peer: RTCPeerConnection,
        local: Boolean,
        description: RTCSessionDescription,
    ) {
        val latch = CountDownLatch(1)
        var failure: String? = null
        val observer = object : SetSessionDescriptionObserver {
            override fun onSuccess() = latch.countDown()
            override fun onFailure(error: String) {
                failure = error
                latch.countDown()
            }
        }
        if (local) peer.setLocalDescription(description, observer)
        else peer.setRemoteDescription(description, observer)
        check(latch.await(15, TimeUnit.SECONDS)) { "WebRTC SDP 设置超时" }
        check(failure == null) { "WebRTC SDP 设置失败：$failure" }
    }

    private fun candidateKey(candidate: SignalingCandidate): String =
        "${candidate.sdpMid}:${candidate.sdpMLineIndex}:${candidate.candidate}"

    private companion object {
        const val OFFER_REFRESH_MARGIN_MILLIS = 5_000L
    }
}

@Serializable
private data class DesktopRpcRequest(
    val type: String = "http.request",
    val requestId: String,
    val offerId: String,
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val bodyBase64: String? = null,
)

@Serializable
private data class DesktopRpcResponse(
    val type: String = "http.response",
    val requestId: String,
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val bodyBase64: String? = null,
)

@Serializable
private data class SignalingDescriptionUpload(
    val offerId: String,
    val type: String,
    val sdp: String,
    val expiresAt: Long,
)

@Serializable
private data class SignalingDescription(
    val offerId: String = "",
    val type: String,
    val sdp: String,
    val revision: Long = 0,
)

@Serializable
private data class SignalingCandidateUpload(
    val side: String,
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int,
)

@Serializable
private data class SignalingCandidate(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int,
)

@Serializable
private data class SignalingCandidates(
    val offerId: String = "",
    val candidates: List<SignalingCandidate> = emptyList(),
)

private data class SignalingResponse(
    val statusCode: Int,
    val body: String,
)
