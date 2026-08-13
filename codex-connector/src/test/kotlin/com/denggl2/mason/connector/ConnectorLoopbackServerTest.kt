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
import com.denggl2.mason.protocol.RemoteAttachmentKind
import com.denggl2.mason.protocol.RemoteConversationAttachment
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteComposerOptions
import com.denggl2.mason.protocol.RemoteConversationCreateRequest
import com.denggl2.mason.protocol.RemoteConversationPage
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.protocol.RemoteExecutionResult
import com.denggl2.mason.protocol.RemoteExecutionStatus
import com.denggl2.mason.protocol.RemoteMessageRequest
import com.denggl2.mason.protocol.SessionGrant
import com.denggl2.mason.protocol.SessionInfo
import com.denggl2.mason.protocol.signingPayload
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ConnectorLoopbackServerTest {
    @Test
    fun conversationAttachmentDownloadRemainsAvailableToExistingConversationReaders() = withLoopbackState { path ->
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
            deviceId = "phone-without-files",
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
        val provider = object : RemoteConversationProvider {
            override suspend fun listConversations(limit: Int, cursor: String?) = RemoteConversationPage(
                conversations = emptyList(),
                nextCursor = null,
            )

            override suspend fun readConversation(threadId: String) = RemoteConversationDetail(
                conversation = RemoteConversationSummary(threadId, "Computer conversation"),
                messages = emptyList(),
            )

            override suspend fun downloadConversationAttachment(
                threadId: String,
                attachmentId: String,
            ) = RemoteConversationAttachmentDownload(
                descriptor = RemoteConversationAttachment(
                    attachmentId = attachmentId,
                    kind = RemoteAttachmentKind.FILE,
                    name = "notes.txt",
                    sizeBytes = 4,
                ),
                bytes = byteArrayOf(1, 2, 3, 4),
            )
        }
        val port = freeLoopbackPort()

        ConnectorLoopbackServer(
            authService = service,
            port = port,
            conversationProvider = provider,
        ).start().use {
            val http = OkHttpClient()
            val baseUrl = "http://127.0.0.1:$port"
            http.post<PairingRequest, PairingResult>(baseUrl, "/v1/pairing/complete", pairingRequest)
            val challenge = http.post<AuthChallengeRequest, AuthChallenge>(
                baseUrl,
                "/v1/auth/challenge",
                AuthChallengeRequest(deviceId = "phone-without-files"),
            )
            val grant = http.post<AuthProof, SessionGrant>(
                baseUrl,
                "/v1/auth/session",
                AuthProof(
                    challengeId = challenge.challengeId,
                    deviceId = "phone-without-files",
                    signature = EcdsaP256Crypto.sign(phoneKeys.private, challenge.signingPayload()),
                ),
            )

            http.newCall(
                Request.Builder()
                    .url("$baseUrl/v1/conversations/thread-1/attachments/attachment-1")
                    .header("Authorization", "Bearer ${grant.sessionToken}")
                    .get()
                    .build(),
            ).execute().use { response ->
                assertTrue(response.isSuccessful, "HTTP ${response.code}")
                assertTrue(response.body!!.bytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
            }
        }
    }

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
            requestedPermissions = setOf(
                DevicePermission.VIEW_SHARED_CONVERSATIONS,
                DevicePermission.SEND_MESSAGES,
                DevicePermission.CONTROL_EXECUTION,
                DevicePermission.REQUEST_FILES,
            ),
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

            override suspend fun downloadConversationAttachment(
                threadId: String,
                attachmentId: String,
            ): RemoteConversationAttachmentDownload {
                assertEquals("thread-1", threadId)
                assertEquals("attachment-1", attachmentId)
                return RemoteConversationAttachmentDownload(
                    descriptor = RemoteConversationAttachment(
                        attachmentId = attachmentId,
                        kind = RemoteAttachmentKind.IMAGE,
                        name = "preview.png",
                        mimeType = "image/png",
                        sizeBytes = 4,
                    ),
                    bytes = byteArrayOf(1, 2, 3, 4),
                )
            }
        }
        var sentText: String? = null
        var interruptedThreadId: String? = null
        var selectedProjectPath: String? = null
        var createRequest: RemoteConversationCreateRequest? = null
        var pinned: Pair<String, Boolean>? = null
        var archivedThreadId: String? = null
        val conversationController = object : RemoteConversationController {
            override suspend fun newConversationOptions(projectPath: String?): RemoteComposerOptions {
                selectedProjectPath = projectPath
                return RemoteComposerOptions(cwd = projectPath)
            }

            override suspend fun createConversation(
                request: RemoteConversationCreateRequest,
            ): RemoteExecutionResult {
                createRequest = request
                return RemoteExecutionResult("thread-created", "turn-created", RemoteExecutionStatus.RUNNING)
            }

            override suspend fun sendMessage(threadId: String, text: String): RemoteExecutionResult {
                if (text == "writer-conflict") {
                    throw CodexRpcException(-32600, "thread $threadId already has an active writer")
                }
                sentText = text
                return RemoteExecutionResult(threadId, "turn-1", RemoteExecutionStatus.RUNNING)
            }

            override suspend fun interrupt(threadId: String): RemoteExecutionResult {
                interruptedThreadId = threadId
                return RemoteExecutionResult(threadId, "turn-1", RemoteExecutionStatus.RUNNING)
            }

            override suspend fun setPinned(threadId: String, isPinned: Boolean): RemoteConversationSummary {
                pinned = threadId to isPinned
                return RemoteConversationSummary(threadId, "电脑会话", isPinned = isPinned)
            }

            override suspend fun archive(threadId: String): RemoteConversationSummary {
                archivedThreadId = threadId
                return RemoteConversationSummary(threadId, "电脑会话")
            }
        }

        ConnectorLoopbackServer(
            authService = service,
            port = port,
            conversationProvider = conversationProvider,
            conversationController = conversationController,
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
                    .url("$baseUrl/v1/conversations/thread-1/attachments/attachment-1")
                    .header("Authorization", "Bearer ${grant.sessionToken}")
                    .get()
                    .build(),
            ).execute().use { response ->
                assertTrue(response.isSuccessful, "HTTP ${response.code}")
                assertEquals("image/png", response.header("Content-Type"))
                assertTrue(response.header("Content-Disposition").orEmpty().contains("preview.png"))
                assertTrue(response.body!!.bytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
            }
            val selectedProject = "D:\\Work\\MASON folder"
            val newOptions = http.getAuthorized<RemoteComposerOptions>(
                "$baseUrl/v1/conversations/new/options".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("projectPath", selectedProject)
                    .build()
                    .toString(),
                grant.sessionToken,
            )
            assertEquals(selectedProject, selectedProjectPath)
            assertEquals(selectedProject, newOptions.cwd)
            val created = http.postAuthorized<RemoteConversationCreateRequest, RemoteExecutionResult>(
                "$baseUrl/v1/conversations",
                grant.sessionToken,
                RemoteConversationCreateRequest(
                    text = "创建会话",
                    projectPath = selectedProject,
                    modelId = "model-1",
                    reasoningEffort = "high",
                    permissionProfileId = ":workspace",
                ),
            )
            assertEquals(selectedProject, createRequest?.projectPath)
            assertEquals("thread-created", created.threadId)
            val sent = http.postAuthorized<RemoteMessageRequest, RemoteExecutionResult>(
                "$baseUrl/v1/conversations/thread-1/messages",
                grant.sessionToken,
                RemoteMessageRequest("继续处理"),
            )
            assertEquals("继续处理", sentText)
            assertEquals(RemoteExecutionStatus.RUNNING, sent.status)
            http.newCall(
                Request.Builder()
                    .url("$baseUrl/v1/conversations/thread-1/messages")
                    .header("Authorization", "Bearer ${grant.sessionToken}")
                    .post(
                        MasonProtocolJson.encode(RemoteMessageRequest("writer-conflict"))
                            .toRequestBody(JSON_MEDIA),
                    )
                    .build(),
            ).execute().use { response ->
                assertEquals(409, response.code)
                assertEquals(
                    "CONVERSATION_ACTIVE_WRITER",
                    MasonProtocolJson.decode<ProtocolErrorResponse>(response.body!!.string()).code,
                )
            }
            val interrupted = http.postAuthorized<RemoteExecutionResult>(
                "$baseUrl/v1/conversations/thread-1/interrupt",
                grant.sessionToken,
            )
            assertEquals("thread-1", interruptedThreadId)
            assertEquals("turn-1", interrupted.turnId)
            val pinnedSummary = http.postAuthorized<RemoteConversationSummary>(
                "$baseUrl/v1/conversations/thread-1/pin",
                grant.sessionToken,
            )
            assertEquals("thread-1" to true, pinned)
            assertTrue(pinnedSummary.isPinned)
            val unpinnedSummary = http.postAuthorized<RemoteConversationSummary>(
                "$baseUrl/v1/conversations/thread-1/unpin",
                grant.sessionToken,
            )
            assertEquals("thread-1" to false, pinned)
            assertFalse(unpinnedSummary.isPinned)
            val archivedSummary = http.postAuthorized<RemoteConversationSummary>(
                "$baseUrl/v1/conversations/thread-1/archive",
                grant.sessionToken,
            )
            assertEquals("thread-1", archivedThreadId)
            assertEquals("thread-1", archivedSummary.threadId)

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

private inline fun <reified RequestType, reified ResponseType> OkHttpClient.postAuthorized(
    url: String,
    token: String,
    requestBody: RequestType,
): ResponseType = newCall(
    Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .post(MasonProtocolJson.encode(requestBody).toRequestBody(JSON_MEDIA))
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
