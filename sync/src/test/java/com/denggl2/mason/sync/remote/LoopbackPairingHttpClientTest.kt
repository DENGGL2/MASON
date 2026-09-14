package com.denggl2.mason.sync.remote

import com.denggl2.mason.protocol.AuthChallenge
import com.denggl2.mason.protocol.AuthProof
import com.denggl2.mason.protocol.Device
import com.denggl2.mason.protocol.DeviceCapability
import com.denggl2.mason.protocol.DeviceKeyAlgorithm
import com.denggl2.mason.protocol.DevicePermission
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingOffer
import com.denggl2.mason.protocol.PairingRequest
import com.denggl2.mason.protocol.PairingResult
import com.denggl2.mason.protocol.Platform
import com.denggl2.mason.protocol.SessionGrant
import com.denggl2.mason.protocol.SessionInfo
import com.denggl2.mason.protocol.signingPayload
import com.denggl2.mason.sync.security.DeviceIdentitySigner
import com.denggl2.mason.sync.security.DevicePublicIdentity
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoopbackPairingHttpClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun clientSignsPairingChallengeAndUsesBearerSession() = runBlocking {
        val signer = TestDeviceSigner()
        val offer = PairingOffer(
            pairingId = "pairing-1",
            connectorDeviceId = "connector-1",
            connectorPublicKey = signer.identity.publicKey,
            connectorPublicKeyFingerprint = "fingerprint",
            oneTimeToken = "one-time-token",
            issuedAt = 100,
            expiresAt = 1_000,
        )
        val challenge = AuthChallenge(
            challengeId = "challenge-1",
            connectorDeviceId = "connector-1",
            deviceId = "phone-1",
            nonce = "nonce",
            issuedAt = 200,
            expiresAt = 1_000,
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/pairing/complete" -> {
                    val pairing = MasonProtocolJson.decode<PairingRequest>(request.body.readUtf8())
                    assertTrue(signer.verify(pairing.signingPayload(), pairing.signature))
                    jsonResponse(
                        PairingResult(
                            ownerId = "owner-1",
                            device = Device(
                                id = pairing.deviceId,
                                ownerId = "owner-1",
                                displayName = pairing.displayName,
                                platform = pairing.platform,
                                publicKey = pairing.publicKey,
                                capabilities = pairing.capabilities,
                            ),
                            grantedPermissions = pairing.requestedPermissions,
                            pairedAt = 150,
                        ),
                    )
                }
                "/v1/auth/challenge" -> jsonResponse(challenge)
                "/v1/auth/session" -> {
                    val proof = MasonProtocolJson.decode<AuthProof>(request.body.readUtf8())
                    assertTrue(signer.verify(challenge.signingPayload(), proof.signature))
                    jsonResponse(
                        SessionGrant(
                            sessionToken = "session-token",
                            deviceId = "phone-1",
                            permissions = setOf(DevicePermission.VIEW_SHARED_CONVERSATIONS),
                            issuedAt = 250,
                            expiresAt = 2_000,
                        ),
                    )
                }
                "/v1/me" -> {
                    assertEquals("Bearer session-token", request.getHeader("Authorization"))
                    jsonResponse(
                        SessionInfo(
                            deviceId = "phone-1",
                            permissions = setOf(DevicePermission.VIEW_SHARED_CONVERSATIONS),
                            expiresAt = 2_000,
                        ),
                    )
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val client = LoopbackPairingHttpClient(server.url("/").toString(), signer)

        val pairing = client.pair(
            offer = offer,
            deviceId = "phone-1",
            displayName = "Phone",
            capabilities = setOf(DeviceCapability.ANDROID_TOOLS),
            requestedPermissions = setOf(DevicePermission.VIEW_SHARED_CONVERSATIONS),
        )
        val grant = client.authenticate("phone-1")
        val session = client.getSession(grant.sessionToken)

        assertEquals("phone-1", pairing.device.id)
        assertEquals("session-token", grant.sessionToken)
        assertEquals("phone-1", session.deviceId)
    }

    @Test
    fun cleartextClientRejectsNonLoopbackHost() {
        assertThrows(IllegalArgumentException::class.java) {
            LoopbackPairingHttpClient("http://192.0.2.1:8080", TestDeviceSigner())
        }
    }
}

private class TestDeviceSigner : DeviceIdentitySigner {
    private val keyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }
    val identity = DevicePublicIdentity(
        keyAlgorithm = DeviceKeyAlgorithm.ECDSA_P256_SHA256,
        publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded),
    )

    override fun getOrCreateIdentity(): DevicePublicIdentity = identity

    override fun sign(payload: String): String = Signature.getInstance("SHA256withECDSA").run {
        initSign(keyPair.private)
        update(payload.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
    }

    fun verify(payload: String, encodedSignature: String): Boolean = Signature.getInstance("SHA256withECDSA").run {
        initVerify(keyPair.public)
        update(payload.toByteArray(Charsets.UTF_8))
        verify(Base64.getDecoder().decode(encodedSignature))
    }
}

private inline fun <reified T> jsonResponse(value: T): MockResponse = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "application/json")
    .setBody(MasonProtocolJson.encode(value))
