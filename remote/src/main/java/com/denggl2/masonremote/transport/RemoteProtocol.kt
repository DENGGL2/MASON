package com.denggl2.masonremote.transport

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val MASON_PROTOCOL_VERSION = 1

object RemoteProtocolJson {
    val format: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    inline fun <reified T> encode(value: T): String = format.encodeToString(value)
    inline fun <reified T> decode(value: String): T = format.decodeFromString(value)
}

@Serializable
enum class DeviceKeyAlgorithm { ECDSA_P256_SHA256 }

@Serializable
enum class TransportMode {
    LOCAL_TLS,
    CLOUDFLARE_TUNNEL,
    WEBRTC_DIRECT,
}

@Serializable
enum class RemoteAgentKind {
    MASON_CODEX,
    CUSTOM,
}

@Serializable
enum class SignalingMode {
    CLIENT_OFFER,
    DESKTOP_OFFER,
}

@Serializable
data class PairingIceServerPayload(
    val urls: List<String> = emptyList(),
    val username: String? = null,
    val credential: String? = null,
)

@Serializable
data class PairingRoutePayload(
    val endpoint: String? = null,
    val signalingEndpoint: String? = null,
    val cloudflareHostname: String? = null,
    val stunServers: List<String> = emptyList(),
    val turnServers: List<PairingIceServerPayload> = emptyList(),
    val signalingMode: SignalingMode = SignalingMode.CLIENT_OFFER,
)

@Serializable
enum class DevicePermission {
    VIEW_SHARED_CONVERSATIONS,
    SEND_MESSAGES,
    CONTROL_EXECUTION,
    RESOLVE_APPROVALS,
    REQUEST_FILES,
}

@Serializable
enum class Platform { ANDROID, WINDOWS, MACOS, IOS, WEB, UNKNOWN }

@Serializable
enum class DeviceCapability {
    CODEX_EXECUTION,
    ANDROID_TOOLS,
    FILE_SEND,
    FILE_RECEIVE,
}

@Serializable
data class PairingOfferPayload(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val pairingId: String,
    val connectorDeviceId: String,
    val connectorPublicKey: String,
    val connectorPublicKeyFingerprint: String,
    val oneTimeToken: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val transportMode: TransportMode = TransportMode.LOCAL_TLS,
    val agentKind: RemoteAgentKind = RemoteAgentKind.MASON_CODEX,
    val offerId: String = pairingId,
    val deviceId: String = connectorDeviceId,
    val publicKey: String = connectorPublicKey,
    val nonce: String = oneTimeToken,
)

@Serializable
data class PairingBootstrapPayload(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val offer: PairingOfferPayload,
    val endpoint: String,
    val tlsCertificateSha256: String,
    val connectorDisplayName: String = "电脑",
    val transportMode: TransportMode = offer.transportMode,
    val agentKind: RemoteAgentKind = offer.agentKind,
    val deviceId: String = offer.deviceId,
    val offerId: String = offer.offerId,
    val publicKey: String = offer.publicKey,
    val nonce: String = offer.nonce,
    val expiresAt: Long = offer.expiresAt,
    val routeBootstrap: PairingRoutePayload = PairingRoutePayload(endpoint = endpoint),
    val signature: String = "",
)

@Serializable
data class PairingRequestPayload(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val pairingId: String,
    val connectorDeviceId: String,
    val oneTimeToken: String,
    val deviceId: String,
    val displayName: String,
    val platform: Platform,
    val keyAlgorithm: DeviceKeyAlgorithm = DeviceKeyAlgorithm.ECDSA_P256_SHA256,
    val publicKey: String,
    val capabilities: Set<DeviceCapability>,
    val requestedPermissions: Set<DevicePermission>,
    val signature: String,
    val transportMode: TransportMode = TransportMode.LOCAL_TLS,
    val agentKind: RemoteAgentKind = RemoteAgentKind.MASON_CODEX,
    val offerId: String = pairingId,
    val nonce: String = oneTimeToken,
    val routeBootstrap: PairingRoutePayload = PairingRoutePayload(),
)

@Serializable
private data class PairingBootstrapSignaturePayload(
    val protocolVersion: Int,
    val transportMode: TransportMode,
    val agentKind: RemoteAgentKind,
    val deviceId: String,
    val offerId: String,
    val publicKey: String,
    val nonce: String,
    val expiresAt: Long,
    val routeBootstrap: PairingRoutePayload,
)

@Serializable
data class PairingDevicePayload(
    val id: String,
    val ownerId: String,
    val displayName: String,
    val platform: Platform,
    val keyAlgorithm: DeviceKeyAlgorithm = DeviceKeyAlgorithm.ECDSA_P256_SHA256,
    val publicKey: String,
    val capabilities: Set<DeviceCapability> = emptySet(),
    val lastSeenAt: Long? = null,
    val revokedAt: Long? = null,
)

@Serializable
data class PairingResultPayload(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val ownerId: String,
    val device: PairingDevicePayload,
    val grantedPermissions: Set<DevicePermission>,
    val pairedAt: Long,
    /** Short-lived signaling route used only to restore this authorized device. */
    val resumeOfferId: String? = null,
)

@Serializable
data class AuthChallengeRequestPayload(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val deviceId: String,
)

@Serializable
data class AuthChallengePayload(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val challengeId: String,
    val connectorDeviceId: String,
    val deviceId: String,
    val nonce: String,
    val issuedAt: Long,
    val expiresAt: Long,
)

@Serializable
data class AuthProofPayload(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val challengeId: String,
    val deviceId: String,
    val signature: String,
)

@Serializable
data class SessionGrantPayload(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val sessionToken: String,
    val deviceId: String,
    val permissions: Set<DevicePermission>,
    val issuedAt: Long,
    val expiresAt: Long,
)

@Serializable
data class ProtocolErrorPayload(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val code: String = "",
    val message: String = "",
)

@Serializable
enum class ConnectorExecutionStatus {
    IDLE,
    RUNNING,
    WAITING_FOR_APPROVAL,
    COMPLETED,
    INTERRUPTED,
    FAILED,
    UNKNOWN,
}

@Serializable
data class ConnectorConversationSummaryPayload(
    val threadId: String,
    val title: String,
    val preview: String = "",
    val latestAttachmentNames: List<String> = emptyList(),
    val updatedAt: Long = 0,
    val projectPath: String? = null,
    val executionStatus: ConnectorExecutionStatus = ConnectorExecutionStatus.IDLE,
    val latestCompletionId: String? = null,
    val isPinned: Boolean = false,
)

@Serializable
data class ConnectorConversationPagePayload(
    val conversations: List<ConnectorConversationSummaryPayload> = emptyList(),
    val nextCursor: String? = null,
    val revision: Long = 0,
)

@Serializable
enum class ConnectorConversationRole { USER, ASSISTANT }

@Serializable
data class ConnectorConversationMessagePayload(
    val role: ConnectorConversationRole,
    val text: String,
    val attachments: List<ConnectorConversationAttachmentPayload> = emptyList(),
)

@Serializable
data class ConnectorConversationAttachmentPayload(
    val attachmentId: String,
    val kind: ConnectorAttachmentKind,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long,
)

@Serializable
enum class ConnectorAttachmentKind { IMAGE, FILE }

@Serializable
enum class ConnectorConversationActivityKind {
    THINKING,
    COMMAND,
    WEB_SEARCH,
    TOOL,
    FILE_CHANGE,
    COMMENTARY,
    PLAN,
    IMAGE,
    OTHER,
}

@Serializable
enum class ConnectorConversationActivityStatus { RUNNING, COMPLETED, INTERRUPTED, FAILED }

@Serializable
data class ConnectorConversationActivityPayload(
    val id: String,
    val kind: ConnectorConversationActivityKind,
    val title: String,
    val text: String = "",
    val status: ConnectorConversationActivityStatus = ConnectorConversationActivityStatus.COMPLETED,
    val command: String? = null,
    val output: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)

@Serializable
data class ConnectorConversationDetailPayload(
    val conversation: ConnectorConversationSummaryPayload,
    val messages: List<ConnectorConversationMessagePayload>,
    val hasEarlierMessages: Boolean = false,
    val executionStatus: ConnectorExecutionStatus = ConnectorExecutionStatus.IDLE,
    val activeTurnId: String? = null,
    val activeActivityTitle: String? = null,
    val activeActivityText: String? = null,
    val activities: List<ConnectorConversationActivityPayload> = emptyList(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val durationMillis: Long? = null,
)

@Serializable
data class ConnectorReasoningEffortOption(
    val id: String,
    val description: String = "",
)

@Serializable
data class ConnectorModelOption(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val defaultReasoningEffort: String = "",
    val supportedReasoningEfforts: List<ConnectorReasoningEffortOption> = emptyList(),
)

internal fun ConnectorModelOption.isCloudXVisible(): Boolean =
    sequenceOf(id, model, displayName, description)
        .none { value -> value.contains("claude", ignoreCase = true) || value.contains("deepseek", ignoreCase = true) }

@Serializable
data class ConnectorPermissionProfileOption(
    val id: String,
    val description: String? = null,
    val allowed: Boolean,
)

@Serializable
data class ConnectorSkillOption(
    val name: String,
    val displayName: String = name,
    val description: String = "",
    val path: String,
    val scope: String = "",
)

@Serializable
data class ConnectorProjectOption(
    val path: String,
    val displayName: String,
)

@Serializable
data class ConnectorComposerOptions(
    val projects: List<ConnectorProjectOption> = emptyList(),
    val models: List<ConnectorModelOption> = emptyList(),
    val skills: List<ConnectorSkillOption> = emptyList(),
    val permissionProfiles: List<ConnectorPermissionProfileOption> = emptyList(),
    val currentModelId: String? = null,
    val currentReasoningEffort: String? = null,
    val currentPermissionProfileId: String? = null,
    val cwd: String? = null,
)

@Serializable
data class ConnectorConversationCreateRequest(
    val text: String,
    val projectPath: String,
    val modelId: String,
    val reasoningEffort: String? = null,
    val permissionProfileId: String,
)

@Serializable
data class ConnectorAttachmentDescriptor(
    val attachmentId: String,
    val kind: ConnectorAttachmentKind,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long,
)

@Serializable
data class ConnectorMessageRequest(
    val text: String = "",
    val attachmentIds: List<String> = emptyList(),
    val skill: ConnectorSkillSelection? = null,
    val modelId: String? = null,
    val reasoningEffort: String? = null,
    val permissionProfileId: String? = null,
    val deliveryMode: ConnectorMessageDeliveryMode = ConnectorMessageDeliveryMode.AUTO,
)

@Serializable
enum class ConnectorMessageDeliveryMode {
    AUTO,
    QUEUE,
    STEER,
}

@Serializable
data class ConnectorSkillSelection(
    val name: String,
    val path: String,
)

@Serializable
data class ConnectorExecutionResult(
    val threadId: String,
    val turnId: String? = null,
    val status: ConnectorExecutionStatus,
    val delivery: ConnectorMessageDelivery = ConnectorMessageDelivery.STARTED,
)

@Serializable
enum class ConnectorMessageDelivery {
    STARTED,
    QUEUED,
    STEERED,
}

@Serializable
data class ConnectorApprovalRequest(
    val threadId: String,
    val requestId: String,
    val method: String,
    val title: String,
    val detail: String = "",
    val params: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
)

@Serializable
data class ConnectorApprovalResolutionRequest(
    val decision: String,
)

@Serializable
data class ConnectorConversationChangePayload(
    val revision: Long,
    val threadId: String,
    val turnId: String? = null,
    val status: ConnectorExecutionStatus,
)

@Serializable
data class ConnectorConversationEventPagePayload(
    val revision: Long = 0,
    val changes: List<ConnectorConversationChangePayload> = emptyList(),
)

@Serializable
data class PairedConnector(
    val connectorDeviceId: String,
    val endpoint: String,
    val tlsCertificateSha256: String,
    val pairedAt: Long,
    val displayName: String,
    val deviceId: String,
    val transportMode: TransportMode = TransportMode.LOCAL_TLS,
    val agentKind: RemoteAgentKind = RemoteAgentKind.MASON_CODEX,
    val routeBootstrap: PairingRoutePayload = PairingRoutePayload(endpoint = endpoint),
    /** The short-lived signaling offer this device was paired through. */
    val offerId: String = connectorDeviceId,
)

fun PairingRequestPayload.signingPayload(): String = RemoteProtocolJson.encode(
    SigningPairingPayload(
        protocolVersion = protocolVersion,
        pairingId = pairingId,
        connectorDeviceId = connectorDeviceId,
        oneTimeToken = oneTimeToken,
        deviceId = deviceId,
        displayName = displayName,
        platform = platform.name,
        keyAlgorithm = keyAlgorithm.name,
        publicKey = publicKey,
        capabilities = capabilities.map(DeviceCapability::name).sorted(),
        requestedPermissions = requestedPermissions.map(DevicePermission::name).sorted(),
        transportMode = transportMode.name,
        agentKind = agentKind.name,
        offerId = offerId,
        nonce = nonce,
        routeBootstrap = routeBootstrap,
    ),
)

fun PairingBootstrapPayload.signingPayload(): String = RemoteProtocolJson.encode(
    PairingBootstrapSignaturePayload(
        protocolVersion = protocolVersion,
        transportMode = transportMode,
        agentKind = agentKind,
        deviceId = deviceId,
        offerId = offerId,
        publicKey = publicKey,
        nonce = nonce,
        expiresAt = expiresAt,
        routeBootstrap = routeBootstrap,
    ),
)

@Serializable
private data class SigningPairingPayload(
    val protocolVersion: Int,
    val pairingId: String,
    val connectorDeviceId: String,
    val oneTimeToken: String,
    val deviceId: String,
    val displayName: String,
    val platform: String,
    val keyAlgorithm: String,
    val publicKey: String,
    val capabilities: List<String>,
    val requestedPermissions: List<String>,
    val transportMode: String,
    val agentKind: String,
    val offerId: String,
    val nonce: String,
    val routeBootstrap: PairingRoutePayload,
)

fun AuthChallengePayload.signingPayload(): String = RemoteProtocolJson.encode(this)
