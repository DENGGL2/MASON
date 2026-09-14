package com.denggl2.mason.sync.remote

import com.denggl2.mason.protocol.AuthChallenge
import com.denggl2.mason.protocol.AuthChallengeRequest
import com.denggl2.mason.protocol.AuthProof
import com.denggl2.mason.protocol.DeviceCapability
import com.denggl2.mason.protocol.DevicePermission
import com.denggl2.mason.protocol.DeviceRevocationResult
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingOffer
import com.denggl2.mason.protocol.PairingBootstrap
import com.denggl2.mason.protocol.PairingRequest
import com.denggl2.mason.protocol.PairingResult
import com.denggl2.mason.protocol.Platform
import com.denggl2.mason.protocol.ProtocolErrorResponse
import com.denggl2.mason.protocol.RemoteAttachmentDescriptor
import com.denggl2.mason.protocol.RemoteAttachmentKind
import com.denggl2.mason.protocol.RemoteComposerOptions
import com.denggl2.mason.protocol.RemoteConversationCreateRequest
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteConversationEventPage
import com.denggl2.mason.protocol.RemoteConversationPage
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.protocol.RemoteExecutionResult
import com.denggl2.mason.protocol.RemoteMessageRequest
import com.denggl2.mason.protocol.SessionGrant
import com.denggl2.mason.protocol.SessionInfo
import com.denggl2.mason.protocol.signingPayload
import com.denggl2.mason.sync.security.DeviceIdentitySigner
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class LoopbackPairingHttpClient(
    baseUrl: String,
    identitySigner: DeviceIdentitySigner,
    httpClient: OkHttpClient = OkHttpClient(),
) {
    private val endpoint: HttpUrl = baseUrl.trimEnd('/').toHttpUrl().also { url ->
        require(url.scheme == "http") { "Loopback pairing currently requires an http URL" }
        require(InetAddress.getByName(url.host).isLoopbackAddress) {
            "Phase 2B loopback client cannot connect to a non-loopback host: ${url.host}"
        }
    }
    private val core = PairingHttpClientCore(endpoint, identitySigner, httpClient)

    suspend fun pair(
        offer: PairingOffer,
        deviceId: String,
        displayName: String,
        capabilities: Set<DeviceCapability>,
        requestedPermissions: Set<DevicePermission>,
    ): PairingResult {
        return core.pair(offer, deviceId, displayName, capabilities, requestedPermissions)
    }

    suspend fun authenticate(deviceId: String): SessionGrant = core.authenticate(deviceId)

    suspend fun getSession(sessionToken: String): SessionInfo = core.getSession(sessionToken)
}

class PinnedPairingHttpsClient(
    private val bootstrap: PairingBootstrap,
    identitySigner: DeviceIdentitySigner,
) {
    private val endpoint = bootstrap.endpoint.trimEnd('/').toHttpUrl().also { url ->
        require(url.scheme == "https") { "Remote pairing requires an https URL" }
    }
    private val pin = normalizeCertificateSha256(bootstrap.tlsCertificateSha256)
    private val core: PairingHttpClientCore

    init {
        core = PairingHttpClientCore(endpoint, identitySigner, buildPinnedHttpClient(pin))
    }

    suspend fun pair(
        deviceId: String,
        displayName: String,
        capabilities: Set<DeviceCapability>,
        requestedPermissions: Set<DevicePermission>,
    ): PairingResult = core.pair(
        bootstrap.offer,
        deviceId,
        displayName,
        capabilities,
        requestedPermissions,
    )

    suspend fun authenticate(deviceId: String): SessionGrant = core.authenticate(deviceId)

    suspend fun getSession(sessionToken: String): SessionInfo = core.getSession(sessionToken)
}

class PinnedConnectorClient(
    connector: PairedConnector,
    identitySigner: DeviceIdentitySigner,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val endpoint = connector.endpoint.trimEnd('/').toHttpUrl().also { url ->
        require(url.scheme == "https") { "Remote Connector requires an https URL" }
    }
    private val core = PairingHttpClientCore(
        endpoint = endpoint,
        identitySigner = identitySigner,
        httpClient = buildPinnedHttpClient(normalizeCertificateSha256(connector.tlsCertificateSha256)),
    )
    private val sessionMutex = Mutex()
    @Volatile
    private var cachedSession: SessionGrant? = null

    suspend fun listConversations(
        deviceId: String,
        limit: Int = 3,
        cursor: String? = null,
    ): RemoteConversationPage {
        require(limit in 1..30) { "Conversation page size must be between 1 and 30" }
        val url = requireNotNull(endpoint.resolve("/v1/conversations"))
            .newBuilder()
            .addQueryParameter("limit", limit.toString())
            .apply { cursor?.takeIf(String::isNotBlank)?.let { addQueryParameter("cursor", it) } }
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.getAuthorized(url, sessionToken)
        }
    }

    suspend fun readConversation(deviceId: String, threadId: String): RemoteConversationDetail {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val url = requireNotNull(endpoint.resolve("/v1/conversations/"))
            .newBuilder()
            .addPathSegment(threadId)
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.getAuthorized(url, sessionToken)
        }
    }

    suspend fun awaitConversationEvents(
        deviceId: String,
        afterRevision: Long,
        waitMillis: Long = DEFAULT_EVENT_WAIT_MILLIS,
    ): RemoteConversationEventPage {
        require(afterRevision >= 0) { "Conversation event revision cannot be negative" }
        require(waitMillis in 0..MAX_EVENT_WAIT_MILLIS) {
            "Conversation event wait must be between 0 and $MAX_EVENT_WAIT_MILLIS milliseconds"
        }
        val url = requireNotNull(endpoint.resolve("/v1/conversation-events"))
            .newBuilder()
            .addQueryParameter("after", afterRevision.toString())
            .addQueryParameter("waitMillis", waitMillis.toString())
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.getAuthorized(url, sessionToken)
        }
    }

    suspend fun composerOptions(deviceId: String, threadId: String): RemoteComposerOptions {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val url = requireNotNull(endpoint.resolve("/v1/conversations/"))
            .newBuilder()
            .addPathSegment(threadId)
            .addPathSegment("composer-options")
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.getAuthorized(url, sessionToken)
        }
    }

    suspend fun newConversationOptions(
        deviceId: String,
        projectPath: String? = null,
    ): RemoteComposerOptions {
        require(projectPath == null || projectPath.isNotBlank()) { "Project path cannot be blank" }
        val url = requireNotNull(endpoint.resolve("/v1/conversations/new/options"))
            .newBuilder()
            .apply { projectPath?.let { addQueryParameter("projectPath", it) } }
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.getAuthorized(url, sessionToken)
        }
    }

    suspend fun createConversation(
        deviceId: String,
        request: RemoteConversationCreateRequest,
    ): RemoteExecutionResult {
        require(request.text.isNotBlank()) { "Message text is required" }
        require(request.projectPath.isNotBlank()) { "Project is required" }
        require(request.modelId.isNotBlank()) { "Model is required" }
        require(request.permissionProfileId.isNotBlank()) { "Permission profile is required" }
        val url = requireNotNull(endpoint.resolve("/v1/conversations"))
        return withSessionRetry(deviceId) { sessionToken ->
            core.postAuthorized(url, sessionToken, request)
        }
    }

    suspend fun uploadAttachment(
        deviceId: String,
        kind: RemoteAttachmentKind,
        name: String,
        mimeType: String?,
        bytes: ByteArray,
    ): RemoteAttachmentDescriptor {
        require(name.isNotBlank()) { "Attachment name is required" }
        require(bytes.isNotEmpty()) { "Attachment is empty" }
        require(bytes.size <= MAX_ATTACHMENT_BYTES) { "Attachment exceeds the 20 MiB limit" }
        val url = requireNotNull(endpoint.resolve("/v1/attachments"))
            .newBuilder()
            .addQueryParameter("kind", kind.name)
            .addQueryParameter("name", name)
            .apply { mimeType?.takeIf(String::isNotBlank)?.let { addQueryParameter("mimeType", it) } }
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.postAuthorizedBytes(url, sessionToken, bytes, mimeType)
        }
    }

    suspend fun downloadConversationAttachment(
        deviceId: String,
        threadId: String,
        attachmentId: String,
    ): ByteArray {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        require(attachmentId.isNotBlank()) { "Attachment ID is required" }
        val url = requireNotNull(endpoint.resolve("/v1/conversations/"))
            .newBuilder()
            .addPathSegment(threadId)
            .addPathSegment("attachments")
            .addPathSegment(attachmentId)
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.getAuthorizedBytes(url, sessionToken, MAX_REMOTE_DOWNLOAD_BYTES)
        }
    }

    suspend fun sendMessage(
        deviceId: String,
        threadId: String,
        text: String,
    ): RemoteExecutionResult = sendMessage(
        deviceId = deviceId,
        threadId = threadId,
        request = RemoteMessageRequest(text = text.trim()),
    )

    suspend fun sendMessage(
        deviceId: String,
        threadId: String,
        request: RemoteMessageRequest,
    ): RemoteExecutionResult {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        require(
            request.text.isNotBlank() || request.attachmentIds.isNotEmpty() || request.skill != null,
        ) { "Message text, attachment, or Skill is required" }
        val url = requireNotNull(endpoint.resolve("/v1/conversations/"))
            .newBuilder()
            .addPathSegment(threadId)
            .addPathSegment("messages")
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.postAuthorized(url, sessionToken, request)
        }
    }

    suspend fun interrupt(deviceId: String, threadId: String): RemoteExecutionResult {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val url = requireNotNull(endpoint.resolve("/v1/conversations/"))
            .newBuilder()
            .addPathSegment(threadId)
            .addPathSegment("interrupt")
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.postAuthorized(url, sessionToken)
        }
    }

    suspend fun pinConversation(deviceId: String, threadId: String): RemoteConversationSummary =
        mutateConversation(deviceId, threadId, "pin")

    suspend fun unpinConversation(deviceId: String, threadId: String): RemoteConversationSummary =
        mutateConversation(deviceId, threadId, "unpin")

    suspend fun archiveConversation(deviceId: String, threadId: String): RemoteConversationSummary =
        mutateConversation(deviceId, threadId, "archive")

    private suspend fun mutateConversation(
        deviceId: String,
        threadId: String,
        action: String,
    ): RemoteConversationSummary {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val url = requireNotNull(endpoint.resolve("/v1/conversations/"))
            .newBuilder()
            .addPathSegment(threadId)
            .addPathSegment(action)
            .build()
        return withSessionRetry(deviceId) { sessionToken ->
            core.postAuthorized(url, sessionToken)
        }
    }

    suspend fun revoke(deviceId: String): DeviceRevocationResult {
        val url = requireNotNull(endpoint.resolve("/v1/me/revoke"))
        val result = withSessionRetry(deviceId) { sessionToken ->
            core.postAuthorized<DeviceRevocationResult>(url, sessionToken)
        }
        sessionMutex.withLock {
            cachedSession = null
        }
        return result
    }

    private suspend fun <T> withSessionRetry(
        deviceId: String,
        request: suspend (sessionToken: String) -> T,
    ): T {
        val initialSession = session(deviceId)
        return try {
            request(initialSession.sessionToken)
        } catch (error: RemotePairingException) {
            if (!error.isRecoverableSessionFailure()) throw error
            sessionMutex.withLock {
                if (cachedSession?.sessionToken == initialSession.sessionToken) {
                    cachedSession = null
                }
            }
            request(session(deviceId).sessionToken)
        }
    }

    private suspend fun session(deviceId: String): SessionGrant {
        cachedSession?.takeIf { it.isReusableFor(deviceId) }?.let { return it }
        return sessionMutex.withLock {
            cachedSession?.takeIf { it.isReusableFor(deviceId) }
                ?: core.authenticate(deviceId).also { cachedSession = it }
        }
    }

    private fun SessionGrant.isReusableFor(deviceId: String): Boolean =
        this.deviceId == deviceId && expiresAt - SESSION_REFRESH_MARGIN_MILLIS > now()

    private companion object {
        const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
        const val MAX_REMOTE_DOWNLOAD_BYTES = 25 * 1024 * 1024
        const val SESSION_REFRESH_MARGIN_MILLIS = 30_000L
        const val DEFAULT_EVENT_WAIT_MILLIS = 25_000L
        const val MAX_EVENT_WAIT_MILLIS = 30_000L
        const val SESSION_INVALID = "SESSION_INVALID"
        const val SESSION_EXPIRED = "SESSION_EXPIRED"
    }

    private fun RemotePairingException.isRecoverableSessionFailure(): Boolean =
        errorCode == SESSION_INVALID || errorCode == SESSION_EXPIRED
}

private class PairingHttpClientCore(
    private val endpoint: HttpUrl,
    private val identitySigner: DeviceIdentitySigner,
    private val httpClient: OkHttpClient,
) {
    suspend fun pair(
        offer: PairingOffer,
        deviceId: String,
        displayName: String,
        capabilities: Set<DeviceCapability>,
        requestedPermissions: Set<DevicePermission>,
    ): PairingResult {
        val identity = identitySigner.getOrCreateIdentity()
        val unsigned = PairingRequest(
            pairingId = offer.pairingId,
            connectorDeviceId = offer.connectorDeviceId,
            oneTimeToken = offer.oneTimeToken,
            deviceId = deviceId,
            displayName = displayName,
            platform = Platform.ANDROID,
            keyAlgorithm = identity.keyAlgorithm,
            publicKey = identity.publicKey,
            capabilities = capabilities,
            requestedPermissions = requestedPermissions,
            signature = "",
        )
        return post(
            path = "/v1/pairing/complete",
            requestBody = unsigned.copy(signature = identitySigner.sign(unsigned.signingPayload())),
        )
    }

    suspend fun authenticate(deviceId: String): SessionGrant {
        val challenge = post<AuthChallengeRequest, AuthChallenge>(
            path = "/v1/auth/challenge",
            requestBody = AuthChallengeRequest(deviceId = deviceId),
        )
        return post(
            path = "/v1/auth/session",
            requestBody = AuthProof(
                challengeId = challenge.challengeId,
                deviceId = deviceId,
                signature = identitySigner.sign(challenge.signingPayload()),
            ),
        )
    }

    suspend fun getSession(sessionToken: String): SessionInfo = withContext(Dispatchers.IO) {
        execute(
            Request.Builder()
                .url(endpoint.resolve("/v1/me") ?: error("Cannot resolve session endpoint"))
                .header("Authorization", "Bearer $sessionToken")
                .get()
                .build(),
        )
    }

    suspend inline fun <reified T> getAuthorized(url: HttpUrl, sessionToken: String): T =
        withContext(Dispatchers.IO) {
            execute(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $sessionToken")
                    .get()
                    .build(),
            )
        }

    suspend fun getAuthorizedBytes(
        url: HttpUrl,
        sessionToken: String,
        maxBytes: Int,
    ): ByteArray = withContext(Dispatchers.IO) {
        httpClient.newCall(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $sessionToken")
                .get()
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val protocolError = runCatching {
                    MasonProtocolJson.decode<ProtocolErrorResponse>(body)
                }.getOrNull()
                throw RemotePairingException(
                    statusCode = response.code,
                    errorCode = protocolError?.code,
                    message = protocolError?.message
                        ?: "Pairing request failed with HTTP ${response.code}",
                )
            }
            val body = requireNotNull(response.body) { "Attachment response is empty" }
            val declaredSize = body.contentLength()
            require(declaredSize < 0L || declaredSize <= maxBytes) {
                "Attachment exceeds the download limit"
            }
            body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes) { "Attachment exceeds the download limit" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }

    suspend inline fun <reified T> postAuthorized(url: HttpUrl, sessionToken: String): T =
        withContext(Dispatchers.IO) {
            execute(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $sessionToken")
                    .post(EMPTY_JSON_BODY)
                    .build(),
            )
        }

    suspend inline fun <reified RequestType, reified ResponseType> postAuthorized(
        url: HttpUrl,
        sessionToken: String,
        requestBody: RequestType,
    ): ResponseType = withContext(Dispatchers.IO) {
        execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $sessionToken")
                .post(MasonProtocolJson.encode(requestBody).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    suspend inline fun <reified T> postAuthorizedBytes(
        url: HttpUrl,
        sessionToken: String,
        bytes: ByteArray,
        mimeType: String?,
    ): T = withContext(Dispatchers.IO) {
        val mediaType = mimeType
            ?.let { value -> runCatching { value.toMediaType() }.getOrNull() }
            ?: OCTET_STREAM_MEDIA_TYPE
        execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $sessionToken")
                .post(bytes.toRequestBody(mediaType))
                .build(),
        )
    }

    private suspend inline fun <reified RequestType, reified ResponseType> post(
        path: String,
        requestBody: RequestType,
    ): ResponseType = withContext(Dispatchers.IO) {
        execute(
            Request.Builder()
                .url(endpoint.resolve(path) ?: error("Cannot resolve endpoint: $path"))
                .post(MasonProtocolJson.encode(requestBody).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    private inline fun <reified T> execute(request: Request): T = httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val protocolError = runCatching { MasonProtocolJson.decode<ProtocolErrorResponse>(body) }.getOrNull()
            throw RemotePairingException(
                statusCode = response.code,
                errorCode = protocolError?.code,
                message = protocolError?.message ?: "Pairing request failed with HTTP ${response.code}",
            )
        }
        MasonProtocolJson.decode(body)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
        private val EMPTY_JSON_BODY = "{}".toRequestBody(JSON_MEDIA_TYPE)
    }
}

private fun buildPinnedHttpClient(pin: String): OkHttpClient {
    val trustManager = PinnedCertificateTrustManager(pin)
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, session ->
            val certificate = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }
                .getOrNull()
            certificate != null && certificateSha256(certificate.encoded) == pin
        }
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()
}

internal class PinnedCertificateTrustManager(
    expectedSha256: String,
) : X509TrustManager {
    private val expectedSha256 = normalizeCertificateSha256(expectedSha256)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not accepted")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificate = chain?.firstOrNull() ?: throw CertificateException("Server certificate is missing")
        certificate.checkValidity()
        if (certificateSha256(certificate.encoded) != expectedSha256) {
            throw CertificateException("Server certificate fingerprint does not match pairing bootstrap")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private fun normalizeCertificateSha256(value: String): String {
    val normalized = value.trim().lowercase()
    require(normalized.length == 64 && normalized.all { it in '0'..'9' || it in 'a'..'f' }) {
        "TLS certificate SHA-256 must be 64 hexadecimal characters"
    }
    return normalized
}

private fun certificateSha256(der: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(der)
    .joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

class RemotePairingException(
    val statusCode: Int,
    val errorCode: String?,
    message: String,
) : IllegalStateException(message)
