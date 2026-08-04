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
    fun privatePairingRejectsLoopbackAndPublicAddresses() {
        assertFailsWith<IllegalArgumentException> {
            validatePrivatePairingHost("127.0.0.1") { true }
        }
        assertFailsWith<IllegalArgumentException> {
            validatePrivatePairingHost("8.8.8.8") { true }
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
}
