package com.denggl2.mason.connector

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudflareTunnelTest {
    @Test
    fun quickTunnelCommandKeepsLoopbackOriginTlsAndQuickMode() {
        assertEquals(
            listOf(
                "cloudflared.exe",
                "tunnel",
                "--no-autoupdate",
                "--url",
                "https://127.0.0.1:42281",
                "--no-tls-verify",
            ),
            CloudflareTunnel.buildCloudflareCommand(
                executable = Path.of("cloudflared.exe"),
                originUrl = "https://127.0.0.1:42281",
            ),
        )
    }

    @Test
    fun namedTunnelCommandUsesStableTunnelAndKeepsTokenOutOfArguments() {
        assertEquals(
            listOf(
                "cloudflared.exe",
                "tunnel",
                "--no-autoupdate",
                "run",
                "--url",
                "https://127.0.0.1:42281",
                "--no-tls-verify",
                "mason-prod",
            ),
            CloudflareTunnel.buildCloudflareCommand(
                executable = Path.of("cloudflared.exe"),
                originUrl = "https://127.0.0.1:42281",
                namedTunnel = "mason-prod",
            ),
        )
        assertEquals(
            listOf(
                "cloudflared.exe",
                "tunnel",
                "--no-autoupdate",
                "run",
                "--url",
                "https://127.0.0.1:42281",
                "--no-tls-verify",
            ),
            CloudflareTunnel.buildCloudflareCommand(
                executable = Path.of("cloudflared.exe"),
                originUrl = "https://127.0.0.1:42281",
                namedTunnel = "ignored-when-token-is-set",
                hasTunnelToken = true,
            ),
        )
    }
}
