package com.denggl2.mason.protocol

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtocolModelsTest {
    @Test
    fun commandRoundTripsWithStableWireType() {
        val command = CommandEnvelope(
            commandId = "command-1",
            deviceId = "device-1",
            issuedAt = 1_000,
            expiresAt = 2_000,
            type = CommandType.EXECUTION_START,
            payload = buildJsonObject { put("conversationId", "conversation-1") },
        )

        val encoded = MasonProtocolJson.encode(command)
        val decoded = MasonProtocolJson.decode<CommandEnvelope>(encoded)

        assertEquals(command, decoded)
        assertTrue(encoded.contains("\"execution.start\""))
    }

    @Test
    fun expiredCommandFailsValidation() {
        val command = CommandEnvelope(
            commandId = "command-1",
            deviceId = "device-1",
            issuedAt = 1_000,
            expiresAt = 2_000,
            type = CommandType.CONVERSATION_RENAME,
        )

        assertEquals(listOf("expiresAt"), command.validate(now = 3_000).map(ProtocolViolation::field))
    }

    @Test
    fun timeSensitiveCommandsCannotQueueOffline() {
        assertFalse(CommandType.APPROVAL_RESOLVE.canQueueWhileOffline())
        assertFalse(CommandType.EXECUTION_INTERRUPT.canQueueWhileOffline())
        assertFalse(CommandType.FILE_REQUEST.canQueueWhileOffline())
        assertTrue(CommandType.CONVERSATION_RENAME.canQueueWhileOffline())
    }

    @Test
    fun eventRejectsNegativeSequence() {
        val event = ConversationEvent(
            eventId = "event-1",
            conversationId = "conversation-1",
            sourceDeviceId = "device-1",
            sequence = -1,
            occurredAt = 1_000,
            type = ConversationEventType.EXECUTION_FAILED,
        )

        assertEquals(listOf("sequence"), event.validate().map(ProtocolViolation::field))
    }

    @Test
    fun pairingPayloadIsStableAcrossSetOrder() {
        val request = PairingRequest(
            pairingId = "pairing-1",
            connectorDeviceId = "connector-1",
            oneTimeToken = "secret",
            deviceId = "phone-1",
            displayName = "Phone",
            platform = Platform.ANDROID,
            publicKey = "public-key",
            capabilities = linkedSetOf(DeviceCapability.FILE_RECEIVE, DeviceCapability.ANDROID_TOOLS),
            requestedPermissions = linkedSetOf(
                DevicePermission.REQUEST_FILES,
                DevicePermission.VIEW_SHARED_CONVERSATIONS,
            ),
            signature = "signature",
        )
        val reordered = request.copy(
            capabilities = request.capabilities.reversed().toSet(),
            requestedPermissions = request.requestedPermissions.reversed().toSet(),
        )

        assertEquals(request.signingPayload(), reordered.signingPayload())
        assertFalse(request.signingPayload().contains("signature"))
        assertTrue(request.signingPayload().contains("ECDSA_P256_SHA256"))
        assertEquals(request, MasonProtocolJson.decode<PairingRequest>(MasonProtocolJson.encode(request)))
    }

    @Test
    fun pairingBootstrapRoundTripsForQrEncoding() {
        val bootstrap = PairingBootstrap(
            offer = PairingOffer(
                pairingId = "pairing-1",
                connectorDeviceId = "connector-1",
                connectorPublicKey = "public-key",
                connectorPublicKeyFingerprint = "public-key-fingerprint",
                oneTimeToken = "one-time-token",
                issuedAt = 100,
                expiresAt = 200,
            ),
            endpoint = "https://100.64.0.1:8443",
            tlsCertificateSha256 = "0123456789abcdef",
        )

        assertEquals(
            bootstrap,
            MasonProtocolJson.decode<PairingBootstrap>(MasonProtocolJson.encode(bootstrap)),
        )
    }

    @Test
    fun remoteConversationPageRoundTripsWithOwnershipAndCursor() {
        val page = RemoteConversationPage(
            conversations = listOf(
                RemoteConversationSummary(
                    threadId = "thread-1",
                    title = "电脑会话",
                    preview = "最近消息",
                    updatedAt = 123,
                    ownership = CodexOwnership.EXTERNAL_HISTORY_ONLY,
                ),
            ),
            nextCursor = "cursor-2",
        )

        assertEquals(page, MasonProtocolJson.decode<RemoteConversationPage>(MasonProtocolJson.encode(page)))
    }

    @Test
    fun pairingRequestRequiresIdentityPermissionAndSignature() {
        val invalid = PairingRequest(
            pairingId = "",
            connectorDeviceId = "",
            oneTimeToken = "",
            deviceId = "",
            displayName = "",
            platform = Platform.ANDROID,
            publicKey = "",
            capabilities = emptySet(),
            requestedPermissions = emptySet(),
            signature = "",
        )

        assertEquals(
            listOf(
                "pairingId",
                "connectorDeviceId",
                "oneTimeToken",
                "deviceId",
                "displayName",
                "publicKey",
                "requestedPermissions",
                "signature",
            ),
            invalid.validate().map(ProtocolViolation::field),
        )
    }
}
