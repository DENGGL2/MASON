package com.denggl2.mason.ui.pairing

import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingBootstrap
import com.denggl2.mason.protocol.PairingOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DevicePairingLogicTest {
    @Test
    fun validBootstrapIsNormalized() {
        val decoded = decodePairingBootstrap(
            MasonProtocolJson.encode(bootstrap(endpoint = "https://100.64.0.1:8443/")),
            now = 100,
        )

        assertEquals("https://100.64.0.1:8443", decoded.endpoint)
        assertEquals("ab".repeat(32), decoded.tlsCertificateSha256)
    }

    @Test
    fun cleartextAndExpiredBootstrapAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            decodePairingBootstrap(
                MasonProtocolJson.encode(bootstrap(endpoint = "http://100.64.0.1:8443")),
                now = 100,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodePairingBootstrap(MasonProtocolJson.encode(bootstrap(expiresAt = 99)), now = 100)
        }
    }
}

private fun bootstrap(
    endpoint: String = "https://100.64.0.1:8443",
    expiresAt: Long = 200,
) = PairingBootstrap(
    offer = PairingOffer(
        pairingId = "pairing-1",
        connectorDeviceId = "connector-1",
        connectorPublicKey = "public-key",
        connectorPublicKeyFingerprint = "public-key-fingerprint",
        oneTimeToken = "one-time-token",
        issuedAt = 50,
        expiresAt = expiresAt,
    ),
    endpoint = endpoint,
    tlsCertificateSha256 = "AB".repeat(32),
)
