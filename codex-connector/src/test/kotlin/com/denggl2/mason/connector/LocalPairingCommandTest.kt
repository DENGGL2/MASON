package com.denggl2.mason.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalPairingCommandTest {
    @Test
    fun privatePairingAcceptsAssignedPrivateIpv4AndNormalizesIt() {
        val host = validatePrivatePairingHost("192.168.1.25") { true }

        assertEquals("192.168.1.25", host)
    }

    @Test
    fun privatePairingAcceptsAssignedTailscaleIpv4() {
        assertEquals("100.64.0.1", validatePrivatePairingHost("100.64.0.1") { true })
        assertEquals("100.127.255.254", validatePrivatePairingHost("100.127.255.254") { true })
    }

    @Test
    fun privatePairingRejectsLoopbackAndPublicAddresses() {
        assertFailsWith<IllegalArgumentException> {
            validatePrivatePairingHost("127.0.0.1") { true }
        }
        assertFailsWith<IllegalArgumentException> {
            validatePrivatePairingHost("8.8.8.8") { true }
        }
        assertFailsWith<IllegalArgumentException> {
            validatePrivatePairingHost("100.63.255.255") { true }
        }
        assertFailsWith<IllegalArgumentException> {
            validatePrivatePairingHost("100.128.0.1") { true }
        }
    }

    @Test
    fun privatePairingRejectsDnsInvalidAndUnassignedAddresses() {
        assertFailsWith<IllegalArgumentException> {
            validatePrivatePairingHost("computer.local") { true }
        }
        assertFailsWith<IllegalArgumentException> {
            validatePrivatePairingHost("192.168.1.999") { true }
        }
        assertFailsWith<IllegalArgumentException> {
            validatePrivatePairingHost("192.168.1.25") { false }
        }
    }

    @Test
    fun webRtcSignalingRequiresHttpsOutsideExplicitLocalTestMode() {
        assertEquals("https://signal.example.test/signaling", validateWebRtcSignalingEndpoint(
            "https://signal.example.test/signaling/",
        ))
        assertFailsWith<IllegalArgumentException> {
            validateWebRtcSignalingEndpoint("http://signal.example.test/signaling", allowInsecureLocal = true)
        }
        assertFailsWith<IllegalArgumentException> {
            validateWebRtcSignalingEndpoint("http://127.0.0.1:48731", allowInsecureLocal = false)
        }
        assertEquals(
            "http://127.0.0.1:48731",
            validateWebRtcSignalingEndpoint("http://127.0.0.1:48731/", allowInsecureLocal = true),
        )
        assertEquals(
            "http://10.0.2.2:48731",
            validateWebRtcSignalingEndpoint("http://10.0.2.2:48731/", allowInsecureLocal = true),
        )
    }

    @Test
    fun cloudflareNamedPairingRequiresPublicDnsHostname() {
        assertEquals("remote.example.com", validateCloudflareHostname(" Remote.Example.com. "))
        assertFailsWith<IllegalArgumentException> {
            validateCloudflareHostname("127.0.0.1")
        }
        assertFailsWith<IllegalArgumentException> {
            validateCloudflareHostname("remote")
        }
        assertFailsWith<IllegalArgumentException> {
            validateCloudflareHostname("https://remote.example.com")
        }
    }
}
