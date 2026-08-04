package com.denggl2.mason.connector

import com.denggl2.mason.protocol.AuthChallenge
import com.denggl2.mason.protocol.AuthChallengeRequest
import com.denggl2.mason.protocol.AuthProof
import com.denggl2.mason.protocol.DeviceCapability
import com.denggl2.mason.protocol.DevicePermission
import com.denggl2.mason.protocol.DeviceRevocationResult
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingRequest
import com.denggl2.mason.protocol.PairingResult
import com.denggl2.mason.protocol.Platform
import com.denggl2.mason.protocol.ProtocolErrorResponse
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteConversationPage
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.protocol.SessionGrant
import com.denggl2.mason.protocol.SessionInfo
import com.denggl2.mason.protocol.signingPayload
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ConnectorLoopbackServerTest {
    @Test
    fun realLoopbackHttpCompletesPairingAuthenticationAndRevocation() = withLoopbackState { path ->
        val connectorKeys = EcdsaP256Crypto.generateKeyPair()
        val phoneKeys = EcdsaP256Crypto.generateKeyPair()
        val store = ConnectorStateStore(
            statePath = path,
            newOwnerId = { "owner-1" },
            newDeviceId = { "connector-1" },
        )
        val service = PairingAuthService(
            store = store,
            connectorPublicKey = EcdsaP256Crypto.encodePublicKey(connectorKeys.public),
        )
        val offer = service.createPairingOffer()
        val unsigned = PairingRequest(
            pairingId = offer.pairingId,
            connectorDeviceId = offer.connectorDeviceId,
            oneTimeToken = offer.oneTimeToken,
            deviceId = "phone-1",
            displayName = "Phone",
            platform = Platform.ANDROID,
            publicKey = EcdsaP256Crypto.encodePublicKey(phoneKeys.public),
            capabilities = setOf(DeviceCapability.ANDROID_TOOLS),
            requestedPermissions = setOf(DevicePermission.VIEW_SHARED_CONVERSATIONS),
            signature = "",
        )
        val pairingRequest = unsigned.copy(
            signature = EcdsaP256Crypto.sign(phoneKeys.private, unsigned.signingPayload()),
        )
        val port = freeLoopbackPort()

        val conversationProvider = object : RemoteConversationProvider {
            override suspend fun listConversations(limit: Int, cursor: String?): RemoteConversationPage {
                assertEquals(3, limit)
                assertEquals("next-1", cursor)
                return RemoteConversationPage(
                    conversations = listOf(RemoteConversationSummary("thread-1", "电脑会话")),
                    nextCursor = "next-2",
                )
            }

            override suspend fun readConversation(threadId: String): RemoteConversationDetail {
                assertEquals("thread-1", threadId)
                return RemoteConversationDetail(
                    conversation = RemoteConversationSummary(threadId, "电脑会话"),
                    messages = emptyList(),
                )
            }
        }

        ConnectorLoopbackServer(
            authService = service,
            port = port,
            conversationProvider = conversationProvider,
        ).start().use {
            val http = OkHttpClient()
            val baseUrl = "http://127.0.0.1:$port"
            val paired = http.post<PairingRequest, PairingResult>(baseUrl, "/v1/pairing/complete", pairingRequest)
            assertEquals("phone-1", paired.device.id)

            val challenge = http.post<AuthChallengeRequest, AuthChallenge>(
                baseUrl,
                "/v1/auth/challenge",
                AuthChallengeRequest(deviceId = "phone-1"),
            )
            val grant = http.post<AuthProof, SessionGrant>(
                baseUrl,
                "/v1/auth/session",
                AuthProof(
                    challengeId = challenge.challengeId,
                    deviceId = "phone-1",
                    signature = EcdsaP256Crypto.sign(phoneKeys.private, challenge.signingPayload()),
                ),
            )
            val session = http.getSession(baseUrl, grant.sessionToken)
            assertEquals("phone-1", session.deviceId)

            val page = http.getAuthorized<RemoteConversationPage>(
                "$baseUrl/v1/conversations?limit=3&cursor=next-1",
                grant.sessionToken,
            )
            assertEquals("thread-1", page.conversations.single().threadId)
            assertEquals("next-2", page.nextCursor)
            val detail = http.getAuthorized<RemoteConversationDetail>(
                "$baseUrl/v1/conversations/thread-1",
                grant.sessionToken,
            )
            assertEquals("电脑会话", detail.conversation.title)

            http.newCall(
                Request.Builder()
                    .url("$baseUrl/v1/auth/challenge")
                    .post("{".toRequestBody(JSON_MEDIA))
                    .build(),
            ).execute().use { response ->
                assertEquals(400, response.code)
                assertEquals(
                    PairingAuthErrorCode.INVALID_REQUEST.name,
                    MasonProtocolJson.decode<ProtocolErrorResponse>(response.body!!.string()).code,
                )
            }

            val revocation = http.postAuthorized<DeviceRevocationResult>(
                "$baseUrl/v1/me/revoke",
                grant.sessionToken,
            )
            assertEquals("phone-1", revocation.deviceId)
            val revokedResponse = http.rawSession(baseUrl, grant.sessionToken)
            revokedResponse.use { response ->
                assertEquals(401, response.code)
                val error = MasonProtocolJson.decode<ProtocolErrorResponse>(response.body!!.string())
                assertEquals(PairingAuthErrorCode.SESSION_INVALID.name, error.code)
            }
        }
    }

    @Test
    fun serverRejectsNonLoopbackBinding() = withLoopbackState { path ->
        val connectorKeys = EcdsaP256Crypto.generateKeyPair()
        val store = ConnectorStateStore(path) { "connector-1" }
        val service = PairingAuthService(
            store = store,
            connectorPublicKey = EcdsaP256Crypto.encodePublicKey(connectorKeys.public),
        )

        assertFailsWith<IllegalArgumentException> {
            ConnectorLoopbackServer(service, host = "0.0.0.0", port = freeLoopbackPort())
        }
    }
}

private inline fun <reified RequestType, reified ResponseType> OkHttpClient.post(
    baseUrl: String,
    path: String,
    body: RequestType,
): ResponseType = newCall(
    Request.Builder()
        .url(baseUrl + path)
        .post(MasonProtocolJson.encode(body).toRequestBody(JSON_MEDIA))
        .build(),
).execute().use { response ->
    val responseBody = response.body!!.string()
    assertTrue(response.isSuccessful, "HTTP ${response.code}: $responseBody")
    MasonProtocolJson.decode(responseBody)
}

private fun OkHttpClient.getSession(baseUrl: String, token: String): SessionInfo =
    rawSession(baseUrl, token).use { response ->
        assertTrue(response.isSuccessful)
        MasonProtocolJson.decode(response.body!!.string())
    }

private fun OkHttpClient.rawSession(baseUrl: String, token: String) = newCall(
    Request.Builder()
        .url("$baseUrl/v1/me")
        .header("Authorization", "Bearer $token")
        .get()
        .build(),
).execute()

private inline fun <reified T> OkHttpClient.getAuthorized(url: String, token: String): T = newCall(
    Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .get()
        .build(),
).execute().use { response ->
    val body = response.body!!.string()
    assertTrue(response.isSuccessful, "HTTP ${response.code}: $body")
    MasonProtocolJson.decode(body)
}

private inline fun <reified T> OkHttpClient.postAuthorized(url: String, token: String): T = newCall(
    Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .post("{}".toRequestBody(JSON_MEDIA))
        .build(),
).execute().use { response ->
    val body = response.body!!.string()
    assertTrue(response.isSuccessful, "HTTP ${response.code}: $body")
    MasonProtocolJson.decode(body)
}

private fun freeLoopbackPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use {
    it.localPort
}

private fun withLoopbackState(block: (Path) -> Unit) {
    val path = Files.createTempFile("mason-loopback-state", ".json")
    Files.deleteIfExists(path)
    try {
        block(path)
    } finally {
        Files.deleteIfExists(path)
    }
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
