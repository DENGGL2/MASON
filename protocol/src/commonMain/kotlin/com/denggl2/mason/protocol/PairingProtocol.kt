package com.denggl2.mason.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceKeyAlgorithm {
    ECDSA_P256_SHA256,
}

@Serializable
enum class DevicePermission {
    VIEW_SHARED_CONVERSATIONS,
    SEND_MESSAGES,
    CONTROL_EXECUTION,
    RESOLVE_APPROVALS,
    REQUEST_FILES,
}

@Serializable
data class PairingOffer(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val pairingId: String,
    val connectorDeviceId: String,
    val connectorPublicKey: String,
    val connectorPublicKeyFingerprint: String,
    val oneTimeToken: String,
    val issuedAt: Long,
    val expiresAt: Long,
)

@Serializable
data class PairingBootstrap(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val offer: PairingOffer,
    val endpoint: String,
    val tlsCertificateSha256: String,
    val connectorDisplayName: String = "电脑",
)

@Serializable
data class PairingRequest(
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
)

@Serializable
data class PairingResult(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val ownerId: String,
    val device: Device,
    val grantedPermissions: Set<DevicePermission>,
    val pairedAt: Long,
)

@Serializable
data class AuthChallenge(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val challengeId: String,
    val connectorDeviceId: String,
    val deviceId: String,
    val nonce: String,
    val issuedAt: Long,
    val expiresAt: Long,
)

@Serializable
data class AuthProof(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val challengeId: String,
    val deviceId: String,
    val signature: String,
)

@Serializable
data class AuthChallengeRequest(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val deviceId: String,
)

@Serializable
data class SessionGrant(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val sessionToken: String,
    val deviceId: String,
    val permissions: Set<DevicePermission>,
    val issuedAt: Long,
    val expiresAt: Long,
)

@Serializable
data class SessionInfo(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val deviceId: String,
    val permissions: Set<DevicePermission>,
    val expiresAt: Long,
)

@Serializable
data class DeviceRevocationResult(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val deviceId: String,
    val revokedAt: Long,
)

@Serializable
data class ProtocolErrorResponse(
    val protocolVersion: Int = MASON_PROTOCOL_VERSION,
    val code: String,
    val message: String,
)

@Serializable
internal data class PairingSignaturePayload(
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
)

fun PairingRequest.signingPayload(): String = MasonProtocolJson.encode(
    PairingSignaturePayload(
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
    ),
)

fun AuthChallenge.signingPayload(): String = MasonProtocolJson.encode(this)

fun PairingRequest.validate(): List<ProtocolViolation> = buildList {
    if (protocolVersion != MASON_PROTOCOL_VERSION) {
        add(ProtocolViolation("protocolVersion", "Unsupported protocol version: $protocolVersion"))
    }
    if (pairingId.isBlank()) add(ProtocolViolation("pairingId", "Pairing ID is required"))
    if (connectorDeviceId.isBlank()) {
        add(ProtocolViolation("connectorDeviceId", "Connector device ID is required"))
    }
    if (oneTimeToken.isBlank()) add(ProtocolViolation("oneTimeToken", "Pairing token is required"))
    if (deviceId.isBlank()) add(ProtocolViolation("deviceId", "Device ID is required"))
    if (displayName.isBlank()) add(ProtocolViolation("displayName", "Display name is required"))
    if (publicKey.isBlank()) add(ProtocolViolation("publicKey", "Public key is required"))
    if (requestedPermissions.isEmpty()) {
        add(ProtocolViolation("requestedPermissions", "At least one permission is required"))
    }
    if (signature.isBlank()) add(ProtocolViolation("signature", "Signature is required"))
}

fun AuthProof.validate(): List<ProtocolViolation> = buildList {
    if (protocolVersion != MASON_PROTOCOL_VERSION) {
        add(ProtocolViolation("protocolVersion", "Unsupported protocol version: $protocolVersion"))
    }
    if (challengeId.isBlank()) add(ProtocolViolation("challengeId", "Challenge ID is required"))
    if (deviceId.isBlank()) add(ProtocolViolation("deviceId", "Device ID is required"))
    if (signature.isBlank()) add(ProtocolViolation("signature", "Signature is required"))
}

fun AuthChallengeRequest.validate(): List<ProtocolViolation> = buildList {
    if (protocolVersion != MASON_PROTOCOL_VERSION) {
        add(ProtocolViolation("protocolVersion", "Unsupported protocol version: $protocolVersion"))
    }
    if (deviceId.isBlank()) add(ProtocolViolation("deviceId", "Device ID is required"))
}
