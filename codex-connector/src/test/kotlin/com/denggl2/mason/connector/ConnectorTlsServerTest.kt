package com.denggl2.mason.connector

import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ConnectorTlsServerTest {
    @Test
    fun tlsServerRejectsWildcardBinding() = withTlsServerFiles { statePath, tlsPath ->
        val connectorKeys = EcdsaP256Crypto.generateKeyPair()
        val service = PairingAuthService(
            store = ConnectorStateStore(statePath) { "connector-1" },
            connectorPublicKey = EcdsaP256Crypto.encodePublicKey(connectorKeys.public),
        )
        ConnectorTlsIdentityStore(tlsPath).getOrCreateIdentity().use { identity ->
            assertFailsWith<IllegalArgumentException> {
                ConnectorTlsServer(service, identity, host = "0.0.0.0", port = freeTlsPort())
            }
        }
    }
}

internal fun freeTlsPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use {
    it.localPort
}

internal fun withTlsServerFiles(block: (statePath: Path, tlsPath: Path) -> Unit) {
    val statePath = Files.createTempFile("mason-tls-state", ".json")
    val tlsPath = Files.createTempFile("mason-tls-identity", ".json")
    Files.deleteIfExists(statePath)
    Files.deleteIfExists(tlsPath)
    try {
        block(statePath, tlsPath)
    } finally {
        Files.deleteIfExists(statePath)
        Files.deleteIfExists(tlsPath)
    }
}
