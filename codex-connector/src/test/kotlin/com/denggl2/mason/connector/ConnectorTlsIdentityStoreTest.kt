package com.denggl2.mason.connector

import com.denggl2.mason.protocol.MasonProtocolJson
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConnectorTlsIdentityStoreTest {
    @Test
    fun dpapiTlsIdentitySurvivesRestartWithStableCertificate() = withTlsIdentityPath { path ->
        val first = ConnectorTlsIdentityStore(path, now = { 1_000 }).getOrCreateIdentity()
        val firstFingerprint = first.certificateSha256
        val firstDer = first.certificateDer
        first.close()

        ConnectorTlsIdentityStore(path, now = { 2_000 }).getOrCreateIdentity().use { second ->
            assertEquals(firstFingerprint, second.certificateSha256)
            assertEquals(firstDer, second.certificateDer)
            assertEquals(1_000, second.createdAt)
            assertTrue(second.keyStore.isKeyEntry(second.keyAlias))
        }
        val stored = MasonProtocolJson.decode<StoredTlsIdentity>(
            Files.readString(path, StandardCharsets.UTF_8),
        )
        assertTrue(stored.protectedBundle.isNotBlank())
        assertTrue(!Files.readString(path).contains("mason-connector-tls"))
    }

    @Test
    fun corruptedDpapiBundleFailsClosed() = withTlsIdentityPath { path ->
        ConnectorTlsIdentityStore(path).getOrCreateIdentity().close()
        val stored = MasonProtocolJson.decode<StoredTlsIdentity>(Files.readString(path))
        Files.writeString(
            path,
            MasonProtocolJson.encode(stored.copy(protectedBundle = "AAECAwQ=")),
            StandardCharsets.UTF_8,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        assertFailsWith<IllegalStateException> {
            ConnectorTlsIdentityStore(path).getOrCreateIdentity()
        }
    }
}

private fun withTlsIdentityPath(block: (Path) -> Unit) {
    val path = Files.createTempFile("mason-connector-tls-identity", ".json")
    Files.deleteIfExists(path)
    try {
        block(path)
    } finally {
        Files.deleteIfExists(path)
    }
}
