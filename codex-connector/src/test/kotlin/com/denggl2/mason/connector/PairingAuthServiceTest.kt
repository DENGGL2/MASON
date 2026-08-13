package com.denggl2.mason.connector

import com.denggl2.mason.protocol.AuthProof
import com.denggl2.mason.protocol.DeviceCapability
import com.denggl2.mason.protocol.DevicePermission
import com.denggl2.mason.protocol.PairingRequest
import com.denggl2.mason.protocol.Platform
import com.denggl2.mason.protocol.signingPayload
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PairingAuthServiceTest {
    @Test
    fun pairingAndChallengeAuthenticationGrantScopedSession() = withFixture { fixture ->
        val pairing = fixture.pairPhone(
            permissions = setOf(
                DevicePermission.VIEW_SHARED_CONVERSATIONS,
                DevicePermission.SEND_MESSAGES,
            ),
        )

        assertEquals("owner-1", pairing.ownerId)
        assertEquals("phone-1", pairing.device.id)
        assertEquals(1, fixture.store.activePairedDevices().size)

        val challenge = fixture.service.createAuthChallenge("phone-1")
        val proof = AuthProof(
            challengeId = challenge.challengeId,
            deviceId = "phone-1",
            signature = EcdsaP256Crypto.sign(fixture.phoneKeys.private, challenge.signingPayload()),
        )
        val grant = fixture.service.authenticate(proof)

        assertEquals("phone-1", fixture.service.authenticateSession(grant.sessionToken).deviceId)
        assertEquals(
            "phone-1",
            fixture.service.authenticateSession(
                grant.sessionToken,
                DevicePermission.SEND_MESSAGES,
            ).deviceId,
        )
        assertAuthError(PairingAuthErrorCode.PERMISSION_DENIED) {
            fixture.service.authenticateSession(grant.sessionToken, DevicePermission.REQUEST_FILES)
        }
        assertAuthError(PairingAuthErrorCode.PERMISSION_DENIED) {
            fixture.service.authenticateSession(
                grant.sessionToken,
                setOf(
                    DevicePermission.VIEW_SHARED_CONVERSATIONS,
                    DevicePermission.REQUEST_FILES,
                ),
            )
        }
    }

    @Test
    fun pairingTokenIsOneTimeAndWrongTokenDoesNotPair() = withFixture { fixture ->
        val offer = fixture.service.createPairingOffer()
        val valid = fixture.signedRequest(offer)
        val invalid = valid.copy(oneTimeToken = "wrong-token", signature = "invalid")

        assertAuthError(PairingAuthErrorCode.INVALID_PAIRING_TOKEN) {
            fixture.service.pair(invalid)
        }
        fixture.service.pair(valid)
        assertAuthError(PairingAuthErrorCode.PAIRING_NOT_FOUND) {
            fixture.service.pair(valid)
        }
    }

    @Test
    fun challengeRejectsWrongSignatureAndCannotBeReplayed() = withFixture { fixture ->
        fixture.pairPhone()
        val attackerKeys = EcdsaP256Crypto.generateKeyPair()
        val challenge = fixture.service.createAuthChallenge("phone-1")
        val invalidProof = AuthProof(
            challengeId = challenge.challengeId,
            deviceId = "phone-1",
            signature = EcdsaP256Crypto.sign(attackerKeys.private, challenge.signingPayload()),
        )

        assertAuthError(PairingAuthErrorCode.INVALID_SIGNATURE) {
            fixture.service.authenticate(invalidProof)
        }
        val validProof = invalidProof.copy(
            signature = EcdsaP256Crypto.sign(fixture.phoneKeys.private, challenge.signingPayload()),
        )
        assertAuthError(PairingAuthErrorCode.CHALLENGE_NOT_FOUND) {
            fixture.service.authenticate(validProof)
        }
    }

    @Test
    fun expiredOfferChallengeAndSessionFailClosed() = withFixture { fixture ->
        val offer = fixture.service.createPairingOffer(ttlMillis = 10)
        fixture.now = 111
        assertAuthError(PairingAuthErrorCode.PAIRING_EXPIRED) {
            fixture.service.pair(fixture.signedRequest(offer))
        }

        fixture.now = 200
        fixture.pairPhone()
        val challenge = fixture.service.createAuthChallenge("phone-1", ttlMillis = 10)
        fixture.now = 211
        assertAuthError(PairingAuthErrorCode.CHALLENGE_EXPIRED) {
            fixture.service.authenticate(fixture.signedProof(challenge))
        }

        fixture.now = 300
        val liveChallenge = fixture.service.createAuthChallenge("phone-1")
        val grant = fixture.service.authenticate(
            fixture.signedProof(liveChallenge),
            sessionTtlMillis = 10,
        )
        fixture.now = 311
        assertAuthError(PairingAuthErrorCode.SESSION_EXPIRED) {
            fixture.service.authenticateSession(grant.sessionToken)
        }
    }

    @Test
    fun revocationInvalidatesSessionsAndSurvivesRestart() = withFixture { fixture ->
        fixture.pairPhone()
        val challenge = fixture.service.createAuthChallenge("phone-1")
        val grant = fixture.service.authenticate(fixture.signedProof(challenge))

        fixture.now = 500
        val revoked = fixture.service.revokeDevice("phone-1")
        assertEquals(500, revoked.device.revokedAt)
        assertAuthError(PairingAuthErrorCode.SESSION_INVALID) {
            fixture.service.authenticateSession(grant.sessionToken)
        }
        assertAuthError(PairingAuthErrorCode.DEVICE_REVOKED) {
            fixture.service.createAuthChallenge("phone-1")
        }

        val restarted = ConnectorStateStore(fixture.path)
        assertEquals(500, restarted.pairedDevice("phone-1")?.device?.revokedAt)
        assertTrue(restarted.activePairedDevices().isEmpty())
    }

    @Test
    fun revokedDeviceCanPairAgainWithAFreshOffer() = withFixture { fixture ->
        fixture.pairPhone()
        fixture.now = 500
        fixture.service.revokeDevice("phone-1")

        fixture.now = 600
        val pairing = fixture.pairPhone()
        val challenge = fixture.service.createAuthChallenge("phone-1")
        val grant = fixture.service.authenticate(fixture.signedProof(challenge))

        assertEquals(600, pairing.pairedAt)
        assertEquals(null, fixture.store.pairedDevice("phone-1")?.device?.revokedAt)
        assertEquals("phone-1", fixture.service.authenticateSession(grant.sessionToken).deviceId)
    }

    @Test
    fun connectorRestartRequiresAFreshChallengeAndSession() = withFixture { fixture ->
        fixture.pairPhone()
        val challenge = fixture.service.createAuthChallenge("phone-1")
        val grant = fixture.service.authenticate(fixture.signedProof(challenge))
        val restarted = PairingAuthService(
            store = ConnectorStateStore(fixture.path),
            connectorPublicKey = EcdsaP256Crypto.encodePublicKey(fixture.connectorKeys.public),
            now = { fixture.now },
        )

        assertAuthError(PairingAuthErrorCode.SESSION_INVALID) {
            restarted.authenticateSession(grant.sessionToken)
        }
        assertEquals("phone-1", restarted.createAuthChallenge("phone-1").deviceId)
    }
}

private class PairingFixture(val path: Path) {
    var now: Long = 100
    private var nextId = 0
    private var randomSeed = 0
    val connectorKeys: KeyPair = EcdsaP256Crypto.generateKeyPair()
    val phoneKeys: KeyPair = EcdsaP256Crypto.generateKeyPair()
    val store = ConnectorStateStore(
        statePath = path,
        newDeviceId = { "connector-1" },
        newOwnerId = { "owner-1" },
    )
    val service = PairingAuthService(
        store = store,
        connectorPublicKey = EcdsaP256Crypto.encodePublicKey(connectorKeys.public),
        now = { now },
        randomBytes = { size -> ByteArray(size) { (++randomSeed).toByte() } },
        newId = { "id-${++nextId}" },
    )

    fun pairPhone(
        permissions: Set<DevicePermission> = DevicePermission.entries.toSet(),
    ) = service.createPairingOffer().let { offer -> service.pair(signedRequest(offer, permissions)) }

    fun signedRequest(
        offer: com.denggl2.mason.protocol.PairingOffer,
        permissions: Set<DevicePermission> = DevicePermission.entries.toSet(),
    ): PairingRequest {
        val unsigned = PairingRequest(
            pairingId = offer.pairingId,
            connectorDeviceId = offer.connectorDeviceId,
            oneTimeToken = offer.oneTimeToken,
            deviceId = "phone-1",
            displayName = "Phone",
            platform = Platform.ANDROID,
            publicKey = EcdsaP256Crypto.encodePublicKey(phoneKeys.public),
            capabilities = setOf(DeviceCapability.ANDROID_TOOLS, DeviceCapability.FILE_RECEIVE),
            requestedPermissions = permissions,
            signature = "",
        )
        return unsigned.copy(signature = EcdsaP256Crypto.sign(phoneKeys.private, unsigned.signingPayload()))
    }

    fun signedProof(challenge: com.denggl2.mason.protocol.AuthChallenge) = AuthProof(
        challengeId = challenge.challengeId,
        deviceId = challenge.deviceId,
        signature = EcdsaP256Crypto.sign(phoneKeys.private, challenge.signingPayload()),
    )
}

private fun withFixture(block: (PairingFixture) -> Unit) {
    val path = Files.createTempFile("mason-pairing", ".json")
    Files.deleteIfExists(path)
    try {
        block(PairingFixture(path))
    } finally {
        Files.deleteIfExists(path)
    }
}

private fun assertAuthError(
    expected: PairingAuthErrorCode,
    block: () -> Unit,
) {
    val error = assertFailsWith<PairingAuthException>(block = block)
    assertEquals(expected, error.code)
}
