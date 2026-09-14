package com.denggl2.mason.connector

import com.denggl2.mason.protocol.DeviceKeyAlgorithm
import com.denggl2.mason.protocol.MasonProtocolJson
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WindowsConnectorIdentityStoreTest {
    @Test
    fun dpapiIdentitySurvivesRestartAndSignsWithStablePublicKey() = withIdentityPath { path ->
        val firstStore = WindowsConnectorIdentityStore(path, now = { 1_000 })
        val first = firstStore.getOrCreateIdentity()
        val payload = "mason-windows-identity-test"
        val signature = firstStore.sign(payload)

        val restarted = WindowsConnectorIdentityStore(path, now = { 2_000 })
        val second = restarted.getOrCreateIdentity()

        assertEquals(DeviceKeyAlgorithm.ECDSA_P256_SHA256, first.keyAlgorithm)
        assertEquals(first, second)
        assertEquals(1_000, second.createdAt)
        assertTrue(
            EcdsaP256Crypto.verify(
                EcdsaP256Crypto.decodePublicKey(second.publicKey),
                payload,
                signature,
            ),
        )
        val stored = MasonProtocolJson.decode<StoredConnectorIdentity>(
            Files.readString(path, StandardCharsets.UTF_8),
        )
        assertTrue(stored.protectedPrivateKey.isNotBlank())
        assertTrue(stored.protectedPrivateKey != first.publicKey)
    }

    @Test
    fun corruptedDpapiCiphertextFailsClosed() = withIdentityPath { path ->
        val store = WindowsConnectorIdentityStore(path)
        store.getOrCreateIdentity()
        val stored = MasonProtocolJson.decode<StoredConnectorIdentity>(
            Files.readString(path, StandardCharsets.UTF_8),
        )
        Files.writeString(
            path,
            MasonProtocolJson.encode(stored.copy(protectedPrivateKey = "AAECAwQ=")),
            StandardCharsets.UTF_8,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        assertFailsWith<IllegalStateException> {
            WindowsConnectorIdentityStore(path).getOrCreateIdentity()
        }
    }
}

private fun withIdentityPath(block: (Path) -> Unit) {
    val path = Files.createTempFile("mason-connector-identity", ".json")
    Files.deleteIfExists(path)
    try {
        block(path)
    } finally {
        Files.deleteIfExists(path)
    }
}
