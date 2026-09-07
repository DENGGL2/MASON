package com.denggl2.masonremote.transport

import android.content.Context
import com.denggl2.masonremote.BuildConfig
import com.denggl2.masonremote.data.AndroidDeviceIdentityStore
import com.denggl2.masonremote.data.PairingStore
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class RealPairingTransport(
    private val pairingStore: PairingStore,
    private val context: Context,
    private val identityStore: AndroidDeviceIdentityStore = AndroidDeviceIdentityStore(),
) : PairingTransport {
    override suspend fun pair(offer: PairingOffer): PairingResult = runCatching {
        validatePairingBootstrap(offer.bootstrap)
        val deviceId = pairingStore.deviceId
        val connector = PairedConnector(
            connectorDeviceId = offer.bootstrap.offer.connectorDeviceId,
            endpoint = offer.bootstrap.endpoint,
            tlsCertificateSha256 = offer.bootstrap.tlsCertificateSha256,
            pairedAt = 0L,
            displayName = offer.deviceName,
            deviceId = deviceId,
            transportMode = offer.bootstrap.transportMode,
            routeBootstrap = offer.bootstrap.routeBootstrap,
            offerId = offer.bootstrap.offerId,
            agentKind = offer.bootstrap.agentKind,
        )
        val client = RemoteConnectorClient(connector, identityStore, context)
        try {
            val pairingResult = client.completePairing(offer.bootstrap)
            client.authenticate(deviceId)
            connector.copy(
                pairedAt = System.currentTimeMillis(),
                offerId = pairingResult.resumeOfferId
                    ?.takeIf(String::isNotBlank)
                    ?: offer.bootstrap.offerId,
            )
        } finally {
            client.close()
        }
    }.fold(
        onSuccess = { PairingResult.Connected(it) },
        onFailure = { PairingResult.Failed(it.toPairingMessage()) },
    )
}

internal class RemoteConnectorClient(
    private val connector: PairedConnector,
    private val identityStore: AndroidDeviceIdentityStore = AndroidDeviceIdentityStore(),
    context: Context? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val endpoint: HttpUrl? = connector.endpoint.trimEnd('/').toHttpUrl().also { url ->
        if (connector.transportMode != TransportMode.WEBRTC_DIRECT) {
            require(url.scheme == "https") { "电脑端连接必须使用 HTTPS" }
        }
    }
    private val httpClient = if (connector.transportMode == TransportMode.WEBRTC_DIRECT) {
        null
    } else {
        buildTransportHttpClient(connector)
    }
    private val webRtcTransport = if (connector.transportMode == TransportMode.WEBRTC_DIRECT) {
        WebRtcTransportRegistry.obtain(
            context = requireNotNull(context) { "WebRTC 传输缺少 Android Context" },
            route = connector.routeBootstrap,
            offerId = connector.offerId,
        )
    } else {
        null
    }
    private val sessionMutex = Mutex()
    @Volatile
    private var cachedSession: SessionGrantPayload? = null

    suspend fun completePairing(bootstrap: PairingBootstrapPayload): PairingResultPayload {
        val identity = identityStore.getOrCreateIdentity()
        val unsigned = PairingRequestPayload(
            pairingId = bootstrap.offer.pairingId,
            connectorDeviceId = bootstrap.offer.connectorDeviceId,
            oneTimeToken = bootstrap.offer.oneTimeToken,
            deviceId = connector.deviceId,
            displayName = android.os.Build.MODEL.ifBlank { "Android device" },
            platform = Platform.ANDROID,
            keyAlgorithm = identity.keyAlgorithm,
            publicKey = identity.publicKey,
            capabilities = setOf(DeviceCapability.ANDROID_TOOLS, DeviceCapability.FILE_RECEIVE),
            requestedPermissions = setOf(
                DevicePermission.VIEW_SHARED_CONVERSATIONS,
                DevicePermission.SEND_MESSAGES,
                DevicePermission.CONTROL_EXECUTION,
                DevicePermission.RESOLVE_APPROVALS,
                DevicePermission.REQUEST_FILES,
            ),
            signature = "",
            transportMode = bootstrap.transportMode,
            agentKind = bootstrap.agentKind,
            offerId = bootstrap.offerId,
            nonce = bootstrap.nonce,
            routeBootstrap = bootstrap.routeBootstrap,
        )
        return post<PairingRequestPayload, PairingResultPayload>(
            "/v1/pairing/complete",
            unsigned.copy(signature = identityStore.sign(unsigned.signingPayload())),
        )
    }

    suspend fun authenticate(deviceId: String): SessionGrantPayload {
        val challenge = post<AuthChallengeRequestPayload, AuthChallengePayload>(
            "/v1/auth/challenge",
            AuthChallengeRequestPayload(deviceId = deviceId),
        )
        return post<AuthProofPayload, SessionGrantPayload>(
            "/v1/auth/session",
            AuthProofPayload(
                challengeId = challenge.challengeId,
                deviceId = deviceId,
                signature = identityStore.sign(challenge.signingPayload()),
            ),
        ).also { cachedSession = it }
    }

    suspend fun listConversations(deviceId: String): ConnectorConversationPagePayload =
        withSessionRetry(deviceId) { token ->
            get("/v1/conversations?limit=30", token)
        }

    suspend fun readConversation(deviceId: String, threadId: String): ConnectorConversationDetailPayload =
        withSessionRetry(deviceId) { token ->
            get("/v1/conversations/$threadId", token)
        }

    suspend fun downloadConversationAttachment(
        deviceId: String,
        threadId: String,
        attachmentId: String,
    ): ByteArray = withSessionRetry(deviceId) { token ->
        getBytes("/v1/conversations/$threadId/attachments/$attachmentId", token)
    }

    suspend fun newConversationOptions(
        deviceId: String,
        projectPath: String? = null,
    ): ConnectorComposerOptions = withSessionRetry(deviceId) { token ->
        get(
            pathWithQuery(
                "/v1/conversations/new/options",
                mapOf("projectPath" to projectPath?.takeIf(String::isNotBlank)),
            ),
            token,
        )
    }

    suspend fun createConversation(
        deviceId: String,
        request: ConnectorConversationCreateRequest,
    ): ConnectorExecutionResult = withSessionRetry(deviceId) { token ->
        postAuthorized("/v1/conversations", token, request.copy(text = request.text.trim()))
    }

    suspend fun awaitConversationEvents(
        deviceId: String,
        afterRevision: Long,
    ): ConnectorConversationEventPagePayload = withSessionRetry(deviceId) { token ->
        get("/v1/conversation-events?after=$afterRevision&waitMillis=1500", token)
    }

    suspend fun pendingApprovals(
        deviceId: String,
        threadId: String,
    ): List<ConnectorApprovalRequest> = withSessionRetry(deviceId) { token ->
        get("/v1/conversations/$threadId/approvals", token)
    }

    suspend fun resolveApproval(
        deviceId: String,
        threadId: String,
        requestId: String,
        decision: String,
    ): ConnectorExecutionResult = withSessionRetry(deviceId) { token ->
        postAuthorized<ConnectorApprovalResolutionRequest, ConnectorExecutionResult>(
            "/v1/conversations/$threadId/approvals/$requestId",
            token,
            ConnectorApprovalResolutionRequest(decision),
        )
    }

    suspend fun composerOptions(
        deviceId: String,
        threadId: String,
    ): ConnectorComposerOptions = withSessionRetry(deviceId) { token ->
        get("/v1/conversations/$threadId/composer-options", token)
    }

    suspend fun uploadAttachment(
        deviceId: String,
        kind: ConnectorAttachmentKind,
        name: String,
        mimeType: String?,
        bytes: ByteArray,
    ): ConnectorAttachmentDescriptor = withSessionRetry(deviceId) { token ->
        postAuthorizedBytes(
            path = pathWithQuery(
                "/v1/attachments",
                mapOf(
                    "kind" to kind.name,
                    "name" to name,
                    "mimeType" to mimeType?.takeIf(String::isNotBlank),
                ),
            ),
            token = token,
            bytes = bytes,
            mediaType = mimeType?.takeIf(String::isNotBlank)?.toMediaType() ?: OCTET_STREAM_MEDIA_TYPE,
        )
    }

    suspend fun sendMessage(
        deviceId: String,
        threadId: String,
        text: String,
    ): ConnectorExecutionResult = withSessionRetry(deviceId) { token ->
        postAuthorized<ConnectorMessageRequest, ConnectorExecutionResult>(
            "/v1/conversations/$threadId/messages",
            token,
            ConnectorMessageRequest(text = text.trim()),
        )
    }

    suspend fun sendMessage(
        deviceId: String,
        threadId: String,
        request: ConnectorMessageRequest,
    ): ConnectorExecutionResult = withSessionRetry(deviceId) { token ->
        postAuthorized(
            "/v1/conversations/$threadId/messages",
            token,
            request.copy(text = request.text.trim()),
        )
    }

    suspend fun interruptConversation(
        deviceId: String,
        threadId: String,
    ): ConnectorExecutionResult = withSessionRetry(deviceId) { token ->
        postAuthorized<ProtocolEmpty, ConnectorExecutionResult>(
            "/v1/conversations/$threadId/interrupt",
            token,
            ProtocolEmpty(),
        )
    }

    suspend fun pinConversation(deviceId: String, threadId: String) =
        mutateConversation(deviceId, threadId, "pin")

    suspend fun unpinConversation(deviceId: String, threadId: String) =
        mutateConversation(deviceId, threadId, "unpin")

    suspend fun archiveConversation(deviceId: String, threadId: String) =
        mutateConversation(deviceId, threadId, "archive")

    suspend fun revoke(deviceId: String) {
        withSessionRetry(deviceId) { token ->
            postAuthorized<ProtocolEmpty, ProtocolEmpty>("/v1/me/revoke", token, ProtocolEmpty())
        }
        sessionMutex.withLock { cachedSession = null }
    }

    private suspend fun mutateConversation(deviceId: String, threadId: String, action: String) =
        withSessionRetry(deviceId) { token ->
            postAuthorized<ProtocolEmpty, ConnectorConversationSummaryPayload>(
                "/v1/conversations/$threadId/$action",
                token,
                ProtocolEmpty(),
            )
        }

    private suspend fun <T> withSessionRetry(
        deviceId: String,
        request: suspend (String) -> T,
    ): T {
        val initial = session(deviceId)
        return try {
            request(initial.sessionToken)
        } catch (error: RemoteConnectorException) {
            if (error.statusCode != 401) throw error
            sessionMutex.withLock {
                if (cachedSession?.sessionToken == initial.sessionToken) cachedSession = null
            }
            request(session(deviceId).sessionToken)
        }
    }

    private suspend fun session(deviceId: String): SessionGrantPayload {
        cachedSession?.takeIf { it.deviceId == deviceId && it.expiresAt - SESSION_REFRESH_MARGIN > now() }
            ?.let { return it }
        return sessionMutex.withLock {
            cachedSession?.takeIf { it.deviceId == deviceId && it.expiresAt - SESSION_REFRESH_MARGIN > now() }
                ?: authenticate(deviceId)
        }
    }

    private suspend inline fun <reified T> get(path: String, token: String): T = executeJson(
        method = "GET",
        path = path,
        headers = mapOf("Authorization" to "Bearer $token"),
    )

    private suspend fun getBytes(path: String, token: String): ByteArray = executeRaw(
        method = "GET",
        path = path,
        headers = mapOf("Authorization" to "Bearer $token"),
    ).validatedBody("电脑端附件读取失败")

    private suspend inline fun <reified RequestType, reified ResponseType> post(
        path: String,
        body: RequestType,
    ): ResponseType = executeJson(
        method = "POST",
        path = path,
        headers = emptyMap(),
        body = RemoteProtocolJson.encode(body).toByteArray(Charsets.UTF_8),
        mediaType = JSON_MEDIA_TYPE,
    )

    private suspend inline fun <reified RequestType, reified ResponseType> postAuthorized(
        path: String,
        token: String,
        body: RequestType,
        retryOnTransportFailure: Boolean = false,
    ): ResponseType = executeJson(
        method = "POST",
        path = path,
        headers = mapOf("Authorization" to "Bearer $token"),
        body = RemoteProtocolJson.encode(body).toByteArray(Charsets.UTF_8),
        mediaType = JSON_MEDIA_TYPE,
        retryOnTransportFailure = retryOnTransportFailure,
    )

    private suspend inline fun <reified ResponseType> postAuthorizedBytes(
        path: String,
        token: String,
        bytes: ByteArray,
        mediaType: okhttp3.MediaType,
    ): ResponseType = executeJson(
        method = "POST",
        path = path,
        headers = mapOf("Authorization" to "Bearer $token"),
        body = bytes,
        mediaType = mediaType,
    )

    private suspend inline fun <reified T> executeJson(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
        mediaType: okhttp3.MediaType = JSON_MEDIA_TYPE,
        retryOnTransportFailure: Boolean = false,
    ): T {
        val response = executeRaw(
            method = method,
            path = path,
            headers = headers,
            body = body,
            mediaType = mediaType,
            retryOnTransportFailure = retryOnTransportFailure,
        )
        val responseBody = response.validatedBody("电脑端请求失败")
        return RemoteProtocolJson.decode(String(responseBody, Charsets.UTF_8))
    }

    private suspend fun executeRaw(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
        mediaType: okhttp3.MediaType = JSON_MEDIA_TYPE,
        retryOnTransportFailure: Boolean = false,
    ): WebRtcHttpResponse {
        val requestHeaders = if (body != null && headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
            headers + ("Content-Type" to mediaType.toString())
        } else {
            headers
        }
        webRtcTransport?.let {
            return it.request(
                method = method,
                path = path,
                headers = requestHeaders,
                body = body,
                retryOnTransportFailure = retryOnTransportFailure,
            )
        }
        val url = requireNotNull(endpoint?.resolve(path)) { "无法解析电脑端地址" }
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            requestHeaders.forEach { (name, value) -> builder.header(name, value) }
            val requestBody = body?.toRequestBody(mediaType)
            builder.method(method, requestBody)
                .build()
                .let { request ->
                    requireNotNull(httpClient).newCall(request).execute().use { response ->
                        WebRtcHttpResponse(
                            statusCode = response.code,
                            headers = emptyMap(),
                            body = response.body?.bytes() ?: ByteArray(0),
                        )
                    }
                }
        }
    }

    private fun pathWithQuery(path: String, parameters: Map<String, String?>): String {
        val base = "http://mason.invalid".toHttpUrl().newBuilder().encodedPath(path)
        parameters.forEach { (name, value) -> value?.let { base.addQueryParameter(name, it) } }
        val url = base.build()
        return url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
    }

    fun close() {
        webRtcTransport?.close()
    }

    private companion object {
        const val SESSION_REFRESH_MARGIN = 30_000L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}

/**
 * Best-effort bridge for host-owned settings. The caller can clear its local
 * pairing first and invoke this without exposing the transport client itself.
 */
suspend fun revokeRemotePairing(
    context: Context,
    connector: PairedConnector,
) {
    val client = RemoteConnectorClient(
        connector = connector,
        identityStore = AndroidDeviceIdentityStore(),
        context = context.applicationContext,
    )
    try {
        client.revoke(connector.deviceId)
    } finally {
        client.close()
    }
}

@kotlinx.serialization.Serializable
private data class ProtocolEmpty(val ignored: Boolean = true)

class RemoteConnectorException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

private fun WebRtcHttpResponse.validatedBody(fallbackMessage: String): ByteArray {
    if (statusCode !in 200..299) {
        val error = runCatching {
            RemoteProtocolJson.decode<ProtocolErrorPayload>(String(body, Charsets.UTF_8))
        }.getOrNull()
        throw RemoteConnectorException(
            statusCode = statusCode,
            message = error?.message?.ifBlank { null } ?: fallbackMessage,
        )
    }
    return body
}

private fun Throwable.toPairingMessage(): String = when {
    this is WebRtcPairingException -> message ?: "RTC 配对失败"
    this is TimeoutCancellationException || hasCause<SocketTimeoutException>() ->
        "RTC 配对超时，请检查手机网络可访问 HTTPS 信令和 TURN 中继"
    hasCause<UnknownHostException>() ->
        "无法解析信令服务器地址，请确认手机网络可访问该 HTTPS 地址"
    hasCause<ConnectException>() ->
        "无法连接信令服务器，请确认 HTTPS 服务已启动并可从公网访问"
    this is RemoteConnectorException -> message ?: "电脑端连接失败"
    else -> message ?: "无法建立电脑端连接"
}

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
    generateSequence(this) { it.cause }.any { it is T }

private fun validatePairingBootstrap(bootstrap: PairingBootstrapPayload) {
    require(bootstrap.protocolVersion == MASON_PROTOCOL_VERSION) { "配对协议版本不受支持" }
    require(bootstrap.offerId == bootstrap.offer.pairingId) { "二维码 offerId 无效" }
    require(bootstrap.deviceId == bootstrap.offer.connectorDeviceId) { "二维码 deviceId 无效" }
    require(bootstrap.publicKey == bootstrap.offer.connectorPublicKey) { "二维码公钥不一致" }
    require(bootstrap.nonce == bootstrap.offer.oneTimeToken) { "二维码 nonce 无效" }
    require(bootstrap.expiresAt == bootstrap.offer.expiresAt) { "二维码过期时间不一致" }
    require(bootstrap.expiresAt > System.currentTimeMillis()) { "配对二维码已过期" }
    require(bootstrap.signature.isNotBlank()) { "二维码缺少 Connector 签名" }
    val publicKey = KeyFactory.getInstance("EC").generatePublic(
        X509EncodedKeySpec(Base64.getDecoder().decode(bootstrap.publicKey)),
    )
    val verified = Signature.getInstance("SHA256withECDSA").run {
        initVerify(publicKey)
        update(bootstrap.signingPayload().toByteArray(Charsets.UTF_8))
        verify(Base64.getDecoder().decode(bootstrap.signature))
    }
    require(verified) { "二维码签名验证失败" }
    when (bootstrap.transportMode) {
        TransportMode.LOCAL_TLS,
        TransportMode.CLOUDFLARE_TUNNEL,
        -> require(!bootstrap.routeBootstrap.endpoint.isNullOrBlank()) { "HTTP 传输缺少 endpoint" }

        TransportMode.WEBRTC_DIRECT -> require(
            !bootstrap.routeBootstrap.signalingEndpoint.isNullOrBlank(),
        ) { "WebRTC 配对缺少 signaling endpoint" }
    }
    if (bootstrap.transportMode == TransportMode.WEBRTC_DIRECT) {
        val signalingEndpoint = requireNotNull(
            bootstrap.routeBootstrap.signalingEndpoint?.trim()?.toHttpUrlOrNull(),
        ) { "WebRTC signaling endpoint 无效" }
        requireSecureSignalingEndpoint(signalingEndpoint)
    }
}

internal fun requireSecureSignalingEndpoint(endpoint: HttpUrl) {
    if (endpoint.scheme == "https") return
    require(BuildConfig.DEBUG && endpoint.host in DEBUG_LOCAL_SIGNALING_HOSTS) {
        "正式 WebRTC 信令必须使用 HTTPS"
    }
}

private val DEBUG_LOCAL_SIGNALING_HOSTS = setOf(
    "10.0.2.2",
    "127.0.0.1",
    "localhost",
)

private fun buildTransportHttpClient(connector: PairedConnector): OkHttpClient = when (connector.transportMode) {
    TransportMode.CLOUDFLARE_TUNNEL -> buildPublicHttpsClient()
    TransportMode.LOCAL_TLS -> buildPinnedHttpClient(connector.tlsCertificateSha256)
    TransportMode.WEBRTC_DIRECT -> error("WebRTC 尚未接入 HTTP 客户端")
}

private fun buildPublicHttpsClient(): OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS)
    .build()

private fun buildPinnedHttpClient(fingerprint: String): OkHttpClient {
    val pin = normalizeFingerprint(fingerprint)
    val trustManager = PinnedCertificateTrustManager(pin)
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }
    val hostnameVerifier = HostnameVerifier { _, session: SSLSession ->
        val certificate = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
        certificate != null && certificateSha256(certificate.encoded) == pin
    }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier(hostnameVerifier)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
}

private class PinnedCertificateTrustManager(
    fingerprint: String,
) : X509TrustManager {
    private val expected = normalizeFingerprint(fingerprint)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("不接受客户端证书")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificate = chain?.firstOrNull() ?: throw CertificateException("电脑端证书缺失")
        certificate.checkValidity()
        if (certificateSha256(certificate.encoded) != expected) {
            throw CertificateException("电脑端证书指纹与配对信息不一致")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private fun normalizeFingerprint(value: String): String {
    val normalized = value.trim().lowercase().replace(":", "")
    require(normalized.length == 64 && normalized.all { it in '0'..'9' || it in 'a'..'f' }) {
        "电脑端证书指纹格式无效"
    }
    return normalized
}

private fun certificateSha256(der: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(der)
    .joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
