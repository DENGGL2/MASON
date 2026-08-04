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
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteConversationPage
import com.denggl2.mason.protocol.SessionGrant
import com.denggl2.mason.protocol.SessionInfo
import com.denggl2.mason.protocol.signingPayload
import com.denggl2.mason.sync.security.DeviceIdentitySigner
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
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
) {
    private val endpoint = connector.endpoint.trimEnd('/').toHttpUrl().also { url ->
        require(url.scheme == "https") { "Remote Connector requires an https URL" }
    }
    private val core = PairingHttpClientCore(
        endpoint = endpoint,
        identitySigner = identitySigner,
        httpClient = buildPinnedHttpClient(normalizeCertificateSha256(connector.tlsCertificateSha256)),
    )

    suspend fun listConversations(
        deviceId: String,
        limit: Int = 3,
        cursor: String? = null,
    ): RemoteConversationPage {
        require(limit in 1..30) { "Conversation page size must be between 1 and 30" }
        val session = core.authenticate(deviceId)
        val url = requireNotNull(endpoint.resolve("/v1/conversations"))
            .newBuilder()
            .addQueryParameter("limit", limit.toString())
            .apply { cursor?.takeIf(String::isNotBlank)?.let { addQueryParameter("cursor", it) } }
            .build()
        return core.getAuthorized(url, session.sessionToken)
    }

    suspend fun readConversation(deviceId: String, threadId: String): RemoteConversationDetail {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val session = core.authenticate(deviceId)
        val url = requireNotNull(endpoint.resolve("/v1/conversations/"))
            .newBuilder()
            .addPathSegment(threadId)
            .build()
        return core.getAuthorized(url, session.sessionToken)
    }

    suspend fun revoke(deviceId: String): DeviceRevocationResult {
        val session = core.authenticate(deviceId)
        val url = requireNotNull(endpoint.resolve("/v1/me/revoke"))
        return core.postAuthorized(url, session.sessionToken)
    }
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
