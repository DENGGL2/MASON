package com.denggl2.mason.sync.remote

import com.denggl2.mason.connector.ConnectorStateStore
import com.denggl2.mason.connector.ConnectorTlsIdentityStore
import com.denggl2.mason.connector.ConnectorTlsServer
import com.denggl2.mason.connector.EcdsaP256Crypto
import com.denggl2.mason.connector.PairingAuthService
import com.denggl2.mason.connector.RemoteConversationProvider
import com.denggl2.mason.connector.RemoteConversationController
import com.denggl2.mason.protocol.DeviceCapability
import com.denggl2.mason.protocol.DeviceKeyAlgorithm
import com.denggl2.mason.protocol.DevicePermission
import com.denggl2.mason.protocol.PairingBootstrap
import com.denggl2.mason.protocol.RemoteAttachmentDescriptor
import com.denggl2.mason.protocol.RemoteAttachmentKind
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteComposerOptions
import com.denggl2.mason.protocol.RemoteConversationPage
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.protocol.RemoteExecutionResult
import com.denggl2.mason.protocol.RemoteExecutionStatus
import com.denggl2.mason.sync.security.DeviceIdentitySigner
import com.denggl2.mason.sync.security.DevicePublicIdentity
import java.net.InetAddress
import java.net.ServerSocket
import javax.net.ssl.SSLHandshakeException
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PinnedPairingHttpsClientTest {
    @Test
    fun correctPinCompletesPairingAndAuthenticationOverRealHttps() = withTlsFixture { fixture ->
        val signer = PinnedTestDeviceSigner()
        val client = PinnedPairingHttpsClient(fixture.bootstrap, signer)

        val pairing = runBlocking {
            client.pair(
                deviceId = "phone-1",
                displayName = "Phone",
                capabilities = setOf(DeviceCapability.ANDROID_TOOLS),
                requestedPermissions = setOf(
                    DevicePermission.VIEW_SHARED_CONVERSATIONS,
                    DevicePermission.SEND_MESSAGES,
                    DevicePermission.CONTROL_EXECUTION,
                ),
            )
        }
        val grant = runBlocking { client.authenticate("phone-1") }
        val session = runBlocking { client.getSession(grant.sessionToken) }

        val connectorClient = PinnedConnectorClient(
            connector = PairedConnector(
                connectorDeviceId = fixture.bootstrap.offer.connectorDeviceId,
                endpoint = fixture.bootstrap.endpoint,
                tlsCertificateSha256 = fixture.bootstrap.tlsCertificateSha256,
                pairedAt = 1,
                displayName = "Test PC",
            ),
            identitySigner = signer,
        )
        val page = runBlocking {
            connectorClient.listConversations("phone-1", limit = 3, cursor = "cursor-1")
        }
        fixture.expireSessions()
        val detail = runBlocking { connectorClient.readConversation("phone-1", "thread-1") }
        val selectedProject = "D:\\Work\\MASON folder"
        val options = runBlocking {
            connectorClient.newConversationOptions("phone-1", selectedProject)
        }
        val uploaded = runBlocking {
            connectorClient.uploadAttachment(
                deviceId = "phone-1",
                kind = RemoteAttachmentKind.IMAGE,
                name = "screen.png",
                mimeType = "image/png",
                bytes = byteArrayOf(1, 2, 3),
            )
        }
        val sent = runBlocking { connectorClient.sendMessage("phone-1", "thread-1", "继续") }
        val interrupted = runBlocking { connectorClient.interrupt("phone-1", "thread-1") }
        val pinned = runBlocking { connectorClient.pinConversation("phone-1", "thread-1") }
        val unpinned = runBlocking { connectorClient.unpinConversation("phone-1", "thread-1") }
        val archived = runBlocking { connectorClient.archiveConversation("phone-1", "thread-1") }
        val revoked = runBlocking { connectorClient.revoke("phone-1") }

        assertEquals("phone-1", pairing.device.id)
        assertEquals("phone-1", session.deviceId)
        assertEquals("thread-1", page.conversations.single().threadId)
        assertEquals("cursor-2", page.nextCursor)
        assertEquals("TLS history", detail.conversation.title)
        assertEquals(selectedProject, options.cwd)
        assertEquals(selectedProject, fixture.selectedProjectPath())
        assertEquals("screen.png", uploaded.name)
        assertEquals(3L, fixture.uploadedSize())
        assertEquals("继续", fixture.sentText())
        assertEquals(RemoteExecutionStatus.RUNNING, sent.status)
        assertEquals("thread-1", fixture.interruptedThreadId())
        assertEquals("turn-1", interrupted.turnId)
        assertEquals(true, pinned.isPinned)
        assertEquals(false, unpinned.isPinned)
        assertEquals("thread-1", archived.threadId)
        assertEquals("thread-1" to false, fixture.pinned())
        assertEquals("thread-1", fixture.archivedThreadId())
        assertEquals("phone-1", revoked.deviceId)
    }

    @Test
    fun wrongPinFailsTlsHandshake() = withTlsFixture { fixture ->
        val wrongBootstrap = fixture.bootstrap.copy(tlsCertificateSha256 = "00".repeat(32))
        val client = PinnedPairingHttpsClient(wrongBootstrap, PinnedTestDeviceSigner())

        assertThrows(SSLHandshakeException::class.java) {
            runBlocking { client.authenticate("not-paired") }
        }
    }

    @Test
    fun remoteClientRejectsHttpDowngrade() {
        val bootstrap = PairingBootstrap(
            offer = testOffer(),
            endpoint = "http://127.0.0.1:8443",
            tlsCertificateSha256 = "01".repeat(32),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PinnedPairingHttpsClient(bootstrap, PinnedTestDeviceSigner())
        }
    }
}

private data class TlsFixture(
    val bootstrap: PairingBootstrap,
    val expireSessions: () -> Unit,
    val sentText: () -> String?,
    val interruptedThreadId: () -> String?,
    val uploadedSize: () -> Long?,
    val selectedProjectPath: () -> String?,
    val pinned: () -> Pair<String, Boolean>?,
    val archivedThreadId: () -> String?,
)

private fun withTlsFixture(block: (TlsFixture) -> Unit) {
    val statePath = Files.createTempFile("mason-pinned-state", ".json")
    val tlsPath = Files.createTempFile("mason-pinned-tls", ".json")
    Files.deleteIfExists(statePath)
    Files.deleteIfExists(tlsPath)
    try {
        val connectorKeys = EcdsaP256Crypto.generateKeyPair()
        var serverNow = System.currentTimeMillis()
        val service = PairingAuthService(
            store = ConnectorStateStore(
                statePath = statePath,
                newOwnerId = { "owner-1" },
                newDeviceId = { "connector-1" },
            ),
            connectorPublicKey = EcdsaP256Crypto.encodePublicKey(connectorKeys.public),
            now = { serverNow },
        )
        val conversationProvider = object : RemoteConversationProvider {
            override suspend fun listConversations(limit: Int, cursor: String?): RemoteConversationPage {
                assertEquals(3, limit)
                assertEquals("cursor-1", cursor)
                return RemoteConversationPage(
                    conversations = listOf(RemoteConversationSummary("thread-1", "TLS history")),
                    nextCursor = "cursor-2",
                )
            }

            override suspend fun readConversation(threadId: String): RemoteConversationDetail =
                RemoteConversationDetail(
                    conversation = RemoteConversationSummary(threadId, "TLS history"),
                    messages = emptyList(),
                )
        }
        var sentText: String? = null
        var interruptedThreadId: String? = null
        var uploadedSize: Long? = null
        var selectedProjectPath: String? = null
        var pinned: Pair<String, Boolean>? = null
        var archivedThreadId: String? = null
        val conversationController = object : RemoteConversationController {
            override suspend fun newConversationOptions(projectPath: String?): RemoteComposerOptions {
                selectedProjectPath = projectPath
                return RemoteComposerOptions(cwd = projectPath)
            }

            override suspend fun uploadAttachment(
                deviceId: String,
                kind: RemoteAttachmentKind,
                name: String,
                mimeType: String?,
                bytes: ByteArray,
            ): RemoteAttachmentDescriptor {
                uploadedSize = bytes.size.toLong()
                return RemoteAttachmentDescriptor(
                    attachmentId = "attachment-1",
                    kind = kind,
                    name = name,
                    mimeType = mimeType,
                    sizeBytes = bytes.size.toLong(),
                )
            }

            override suspend fun sendMessage(threadId: String, text: String): RemoteExecutionResult {
                sentText = text
                return RemoteExecutionResult(threadId, "turn-1", RemoteExecutionStatus.RUNNING)
            }

            override suspend fun interrupt(threadId: String): RemoteExecutionResult {
                interruptedThreadId = threadId
                return RemoteExecutionResult(threadId, "turn-1", RemoteExecutionStatus.RUNNING)
            }

            override suspend fun setPinned(threadId: String, isPinned: Boolean): RemoteConversationSummary {
                pinned = threadId to isPinned
                return RemoteConversationSummary(threadId, "TLS history", isPinned = isPinned)
            }

            override suspend fun archive(threadId: String): RemoteConversationSummary {
                archivedThreadId = threadId
                return RemoteConversationSummary(threadId, "TLS history")
            }
        }
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        ConnectorTlsIdentityStore(tlsPath).getOrCreateIdentity().use { identity ->
            ConnectorTlsServer(
                authService = service,
                tlsIdentity = identity,
                host = "127.0.0.1",
                port = port,
                conversationProvider = conversationProvider,
                conversationController = conversationController,
            ).start().use {
                block(
                    TlsFixture(
                        bootstrap = PairingBootstrap(
                            offer = service.createPairingOffer(),
                            endpoint = "https://127.0.0.1:$port",
                            tlsCertificateSha256 = identity.certificateSha256,
                        ),
                        expireSessions = {
                            serverNow += PairingAuthService.DEFAULT_SESSION_TTL_MILLIS + 1
                        },
                        sentText = { sentText },
                        interruptedThreadId = { interruptedThreadId },
                        uploadedSize = { uploadedSize },
                        selectedProjectPath = { selectedProjectPath },
                        pinned = { pinned },
                        archivedThreadId = { archivedThreadId },
                    ),
                )
            }
        }
    } finally {
        Files.deleteIfExists(statePath)
        Files.deleteIfExists(tlsPath)
    }
}

private class PinnedTestDeviceSigner : DeviceIdentitySigner {
    private val keyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }
    private val identity = DevicePublicIdentity(
        keyAlgorithm = DeviceKeyAlgorithm.ECDSA_P256_SHA256,
        publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded),
    )

    override fun getOrCreateIdentity(): DevicePublicIdentity = identity

    override fun sign(payload: String): String = Signature.getInstance("SHA256withECDSA").run {
        initSign(keyPair.private)
        update(payload.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
    }
}

private fun testOffer() = com.denggl2.mason.protocol.PairingOffer(
    pairingId = "pairing-1",
    connectorDeviceId = "connector-1",
    connectorPublicKey = "public-key",
    connectorPublicKeyFingerprint = "fingerprint",
    oneTimeToken = "token",
    issuedAt = 1,
    expiresAt = 2,
)
