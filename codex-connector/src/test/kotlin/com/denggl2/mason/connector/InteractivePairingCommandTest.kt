package com.denggl2.mason.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InteractivePairingCommandTest {
    @Test
    fun parsesBothInteractiveTransportChoices() {
        assertEquals(InteractivePairingMode.CLOUDFLARE, parseInteractivePairingMode("1"))
        assertEquals(InteractivePairingMode.CLOUDFLARE, parseInteractivePairingMode("Cloudflare"))
        assertEquals(InteractivePairingMode.WEBRTC, parseInteractivePairingMode("2"))
        assertEquals(InteractivePairingMode.WEBRTC, parseInteractivePairingMode("WebRTC direct"))
    }

    @Test
    fun rejectsUnknownInteractiveTransportChoice() {
        assertFailsWith<IllegalArgumentException> {
            parseInteractivePairingMode("3")
        }
    }

    @Test
    fun parsesConfiguredIceServers() {
        assertEquals(
            listOf("stun:one.example:3478", "stuns:two.example:5349"),
            parseWebRtcStunServers("stun:one.example:3478, stuns:two.example:5349"),
        )
        assertEquals(
            listOf(
                com.denggl2.mason.protocol.PairingIceServer(
                    urls = listOf("turns:turn.example:5349", "turn:turn.example:3478"),
                    username = "user",
                    credential = "credential",
                ),
            ),
            parseWebRtcTurnServers(
                "turns:turn.example:5349,turn:turn.example:3478|user|credential",
            ),
        )
    }

    @Test
    fun rejectsInvalidConfiguredIceServers() {
        assertFailsWith<IllegalArgumentException> {
            parseWebRtcStunServers("https://stun.example")
        }
        assertFailsWith<IllegalArgumentException> {
            parseWebRtcTurnServers("turn:turn.example|user")
        }
    }
}
