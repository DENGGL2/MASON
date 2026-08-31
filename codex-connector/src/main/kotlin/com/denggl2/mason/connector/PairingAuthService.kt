package com.denggl2.mason.connector

import com.denggl2.mason.protocol.AuthChallenge
import com.denggl2.mason.protocol.AuthProof
import com.denggl2.mason.protocol.Device
import com.denggl2.mason.protocol.DeviceKeyAlgorithm
import com.denggl2.mason.protocol.DevicePermission
import com.denggl2.mason.protocol.PairingOffer
import com.denggl2.mason.protocol.PairingRequest
import com.denggl2.mason.protocol.PairingResult
import com.denggl2.mason.protocol.RemoteAgentKind
import com.denggl2.mason.protocol.SessionGrant
import com.denggl2.mason.protocol.TransportMode
import com.denggl2.mason.protocol.signingPayload
import com.denggl2.mason.protocol.validate
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

enum class PairingAuthErrorCode {
    INVALID_REQUEST,
    PAIRING_NOT_FOUND,
    PAIRING_EXPIRED,
    INVALID_PAIRING_TOKEN,
    CONNECTOR_MISMATCH,
    INVALID_PUBLIC_KEY,
    INVALID_SIGNATURE,
    DEVICE_ALREADY_PAIRED,
    DEVICE_NOT_PAIRED,
    DEVICE_REVOKED,
    CHALLENGE_NOT_FOUND,
    CHALLENGE_EXPIRED,
    SESSION_INVALID,
    SESSION_EXPIRED,
    PERMISSION_DENIED,
}

class PairingAuthException(
    val code: PairingAuthErrorCode,
    message: String,
) : IllegalStateException(message)

data class SessionPrincipal(
    val deviceId: String,
    val permissions: Set<DevicePermission>,
    val expiresAt: Long,
)

object EcdsaP256Crypto {
    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance(KEY_ALGORITHM).run {
        initialize(ECGenParameterSpec(CURVE_NAME))
        generateKeyPair()
    }

    fun encodePublicKey(publicKey: PublicKey): String =
        Base64.getEncoder().encodeToString(publicKey.encoded)

    fun decodePublicKey(encoded: String): PublicKey {
        val publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
        require(publicKey is ECPublicKey && publicKey.params.matchesP256()) {
            "Public key must use the P-256 curve"
        }
        return publicKey
    }

    fun sign(privateKey: PrivateKey, payload: String): String = Signature.getInstance(SIGNATURE_ALGORITHM).run {
        initSign(privateKey)
        update(payload.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
    }

    fun verify(publicKey: PublicKey, payload: String, encodedSignature: String): Boolean = runCatching {
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(publicKey)
            update(payload.toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(encodedSignature))
        }
    }.getOrDefault(false)

    fun fingerprint(encodedPublicKey: String): String = connectorSha256Bytes(
        Base64.getDecoder().decode(encodedPublicKey),
    ).toHex()

    private fun ECParameterSpec.matchesP256(): Boolean {
        val expected = AlgorithmParameters.getInstance(KEY_ALGORITHM).run {
            init(ECGenParameterSpec(CURVE_NAME))
            getParameterSpec(ECParameterSpec::class.java)
        }
        return curve == expected.curve &&
            generator == expected.generator &&
            order == expected.order &&
            cofactor == expected.cofactor
    }

    private const val KEY_ALGORITHM = "EC"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val CURVE_NAME = "secp256r1"
}

class PairingAuthService(
    private val store: ConnectorStateStore,
    private val connectorPublicKey: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val randomBytes: (Int) -> ByteArray = { size -> ByteArray(size).also(SecureRandom()::nextBytes) },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val allowedPermissions: Set<DevicePermission> = DevicePermission.entries.toSet(),
) {
    private val lock = Any()
    private val pairingOffers = mutableMapOf<String, PendingPairing>()
    private val challenges = mutableMapOf<String, AuthChallenge>()
    private val sessions = mutableMapOf<String, SessionPrincipal>()

    init {
        runCatching { EcdsaP256Crypto.decodePublicKey(connectorPublicKey) }.getOrElse {
            throw IllegalArgumentException("Connector public key must be a valid P-256 X.509 key", it)
        }
    }

    fun createPairingOffer(
        ttlMillis: Long = DEFAULT_PAIRING_TTL_MILLIS,
        transportMode: TransportMode = TransportMode.LOCAL_TLS,
        agentKind: RemoteAgentKind = RemoteAgentKind.MASON_CODEX,
    ): PairingOffer = synchronized(lock) {
        require(ttlMillis > 0) { "Pairing TTL must be positive" }
        val issuedAt = now()
        val pairingId = newId()
        val token = randomToken()
        val offer = PairingOffer(
            pairingId = pairingId,
            connectorDeviceId = store.deviceId,
            connectorPublicKey = connectorPublicKey,
            connectorPublicKeyFingerprint = EcdsaP256Crypto.fingerprint(connectorPublicKey),
            oneTimeToken = token,
            issuedAt = issuedAt,
            expiresAt = issuedAt + ttlMillis,
            transportMode = transportMode,
            agentKind = agentKind,
        )
        pairingOffers[pairingId] = PendingPairing(
            tokenDigest = connectorSha256Bytes(token.toByteArray(Charsets.UTF_8)),
            expiresAt = offer.expiresAt,
            transportMode = transportMode,
            agentKind = agentKind,
        )
        offer
    }

    fun pair(request: PairingRequest): PairingResult = synchronized(lock) {
        if (request.validate().isNotEmpty()) fail(PairingAuthErrorCode.INVALID_REQUEST, "Invalid pairing request")
        if (request.connectorDeviceId != store.deviceId) {
            fail(PairingAuthErrorCode.CONNECTOR_MISMATCH, "Pairing request targets another Connector")
        }
        if (request.offerId != request.pairingId) {
            fail(PairingAuthErrorCode.INVALID_REQUEST, "Pairing offer ID does not match pairing ID")
        }
        val pending = pairingOffers[request.pairingId]
            ?: fail(PairingAuthErrorCode.PAIRING_NOT_FOUND, "Pairing offer does not exist")
        if (pending.expiresAt <= now()) {
            pairingOffers.remove(request.pairingId)
            fail(PairingAuthErrorCode.PAIRING_EXPIRED, "Pairing offer has expired")
        }
        val suppliedDigest = connectorSha256Bytes(request.oneTimeToken.toByteArray(Charsets.UTF_8))
        if (!MessageDigest.isEqual(pending.tokenDigest, suppliedDigest)) {
            fail(PairingAuthErrorCode.INVALID_PAIRING_TOKEN, "Pairing token is invalid")
        }
        if (request.transportMode != pending.transportMode) {
            fail(PairingAuthErrorCode.INVALID_REQUEST, "Pairing transport mode does not match the offer")
        }
        if (request.agentKind != pending.agentKind) {
            fail(PairingAuthErrorCode.INVALID_REQUEST, "Pairing agent does not match the offer")
        }
        if (request.nonce != request.oneTimeToken) {
            fail(PairingAuthErrorCode.INVALID_REQUEST, "Pairing nonce does not match the one-time token")
        }
        if (request.keyAlgorithm != DeviceKeyAlgorithm.ECDSA_P256_SHA256) {
            fail(PairingAuthErrorCode.INVALID_PUBLIC_KEY, "Unsupported device key algorithm")
        }
        val publicKey = runCatching { EcdsaP256Crypto.decodePublicKey(request.publicKey) }.getOrElse {
            fail(PairingAuthErrorCode.INVALID_PUBLIC_KEY, "Device public key is invalid")
        }
        if (!EcdsaP256Crypto.verify(publicKey, request.signingPayload(), request.signature)) {
            fail(PairingAuthErrorCode.INVALID_SIGNATURE, "Pairing proof signature is invalid")
        }
        store.pairedDevice(request.deviceId)?.let { existing ->
            if (existing.device.revokedAt == null) {
                fail(PairingAuthErrorCode.DEVICE_ALREADY_PAIRED, "Device ID is already paired")
            }
        }
        val grantedPermissions = request.requestedPermissions intersect allowedPermissions
        if (grantedPermissions.isEmpty()) {
            fail(PairingAuthErrorCode.PERMISSION_DENIED, "No requested permission can be granted")
        }

        val pairedAt = now()
        val device = Device(
            id = request.deviceId,
            ownerId = store.ownerId,
            displayName = request.displayName.trim(),
            platform = request.platform,
            keyAlgorithm = request.keyAlgorithm,
            publicKey = request.publicKey,
            capabilities = request.capabilities,
            lastSeenAt = pairedAt,
        )
        store.registerPairedDevice(
            PairedDeviceSnapshot(
                device = device,
                permissions = grantedPermissions,
                publicKeyFingerprint = EcdsaP256Crypto.fingerprint(request.publicKey),
                pairedAt = pairedAt,
            ),
        )
        pairingOffers.remove(request.pairingId)
        PairingResult(
            ownerId = store.ownerId,
            device = device,
            grantedPermissions = grantedPermissions,
            pairedAt = pairedAt,
        )
    }

    fun createAuthChallenge(
        deviceId: String,
        ttlMillis: Long = DEFAULT_CHALLENGE_TTL_MILLIS,
    ): AuthChallenge = synchronized(lock) {
        require(ttlMillis > 0) { "Challenge TTL must be positive" }
        activeDevice(deviceId)
        val issuedAt = now()
        AuthChallenge(
            challengeId = newId(),
            connectorDeviceId = store.deviceId,
            deviceId = deviceId,
            nonce = randomToken(),
            issuedAt = issuedAt,
            expiresAt = issuedAt + ttlMillis,
        ).also { challenge -> challenges[challenge.challengeId] = challenge }
    }

    fun authenticate(
        proof: AuthProof,
        sessionTtlMillis: Long = DEFAULT_SESSION_TTL_MILLIS,
    ): SessionGrant = synchronized(lock) {
        require(sessionTtlMillis > 0) { "Session TTL must be positive" }
        if (proof.validate().isNotEmpty()) fail(PairingAuthErrorCode.INVALID_REQUEST, "Invalid auth proof")
        val challenge = challenges.remove(proof.challengeId)
            ?: fail(PairingAuthErrorCode.CHALLENGE_NOT_FOUND, "Auth challenge does not exist")
        if (challenge.deviceId != proof.deviceId) {
            fail(PairingAuthErrorCode.INVALID_SIGNATURE, "Auth proof device does not match challenge")
        }
        if (challenge.expiresAt <= now()) {
            fail(PairingAuthErrorCode.CHALLENGE_EXPIRED, "Auth challenge has expired")
        }
        val paired = activeDevice(proof.deviceId)
        if (paired.device.keyAlgorithm != DeviceKeyAlgorithm.ECDSA_P256_SHA256) {
            fail(PairingAuthErrorCode.INVALID_PUBLIC_KEY, "Unsupported stored device key algorithm")
        }
        val publicKey = runCatching { EcdsaP256Crypto.decodePublicKey(paired.device.publicKey) }.getOrElse {
            fail(PairingAuthErrorCode.INVALID_PUBLIC_KEY, "Stored device public key is invalid")
        }
        if (!EcdsaP256Crypto.verify(publicKey, challenge.signingPayload(), proof.signature)) {
            fail(PairingAuthErrorCode.INVALID_SIGNATURE, "Auth proof signature is invalid")
        }

        val issuedAt = now()
        val token = randomToken()
        val principal = SessionPrincipal(
            deviceId = proof.deviceId,
            permissions = paired.permissions,
            expiresAt = issuedAt + sessionTtlMillis,
        )
        sessions[tokenKey(token)] = principal
        SessionGrant(
            sessionToken = token,
            deviceId = proof.deviceId,
            permissions = paired.permissions,
            issuedAt = issuedAt,
            expiresAt = principal.expiresAt,
        )
    }

    fun authenticateSession(
        sessionToken: String,
        requiredPermission: DevicePermission? = null,
    ): SessionPrincipal = authenticateSession(
        sessionToken = sessionToken,
        requiredPermissions = setOfNotNull(requiredPermission),
    )

    fun authenticateSession(
        sessionToken: String,
        requiredPermissions: Set<DevicePermission>,
    ): SessionPrincipal = synchronized(lock) {
        val key = tokenKey(sessionToken)
        val principal = sessions[key]
            ?: fail(PairingAuthErrorCode.SESSION_INVALID, "Session token is invalid")
        if (principal.expiresAt <= now()) {
            sessions.remove(key)
            fail(PairingAuthErrorCode.SESSION_EXPIRED, "Session token has expired")
        }
        activeDevice(principal.deviceId)
        val missingPermissions = requiredPermissions - principal.permissions
        if (missingPermissions.isNotEmpty()) {
            fail(
                PairingAuthErrorCode.PERMISSION_DENIED,
                "Session does not grant ${missingPermissions.joinToString()}",
            )
        }
        principal
    }

    fun revokeDevice(deviceId: String): PairedDeviceSnapshot = synchronized(lock) {
        val revoked = store.revokeDevice(deviceId, now())
            ?: fail(PairingAuthErrorCode.DEVICE_NOT_PAIRED, "Device is not paired")
        challenges.entries.removeIf { (_, challenge) -> challenge.deviceId == deviceId }
        sessions.entries.removeIf { (_, principal) -> principal.deviceId == deviceId }
        revoked
    }

    private fun activeDevice(deviceId: String): PairedDeviceSnapshot {
        val paired = store.pairedDevice(deviceId)
            ?: fail(PairingAuthErrorCode.DEVICE_NOT_PAIRED, "Device is not paired")
        if (paired.device.revokedAt != null) {
            fail(PairingAuthErrorCode.DEVICE_REVOKED, "Device has been revoked")
        }
        return paired
    }

    private fun randomToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(TOKEN_BYTES))

    private fun tokenKey(token: String): String =
        connectorSha256Bytes(token.toByteArray(Charsets.UTF_8)).toHex()

    private data class PendingPairing(
        val tokenDigest: ByteArray,
        val expiresAt: Long,
        val transportMode: TransportMode,
        val agentKind: RemoteAgentKind,
    )

    companion object {
        const val DEFAULT_PAIRING_TTL_MILLIS = 5 * 60 * 1_000L
        const val DEFAULT_CHALLENGE_TTL_MILLIS = 60 * 1_000L
        const val DEFAULT_SESSION_TTL_MILLIS = 15 * 60 * 1_000L
        private const val TOKEN_BYTES = 32
    }
}

private fun fail(code: PairingAuthErrorCode, message: String): Nothing =
    throw PairingAuthException(code, message)

private fun connectorSha256Bytes(value: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(value)

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
}
