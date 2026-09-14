package com.denggl2.mason.connector

import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingBootstrap
import com.denggl2.mason.protocol.PairingOffer
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PairingQrCodeWriterTest {
    @Test
    fun generatedPngDecodesToExactBootstrapJson() {
        val path = Files.createTempFile("mason-pairing", ".png")
        Files.deleteIfExists(path)
        try {
            val bootstrap = testBootstrap()
            val written = PairingQrCodeWriter.write(bootstrap, path)
            val image = ImageIO.read(written.toFile())
            val decoded = MultiFormatReader().decode(
                BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(image))),
            )

            assertEquals(MasonProtocolJson.encode(bootstrap), decoded.text)
            assertTrue(Files.size(written) > 0)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun writerRefusesToOverwriteExistingOutput() {
        val path = Files.createTempFile("mason-pairing-existing", ".png")
        try {
            assertFailsWith<IllegalArgumentException> {
                PairingQrCodeWriter.write(testBootstrap(), path)
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}

private fun testBootstrap() = PairingBootstrap(
    offer = PairingOffer(
        pairingId = "pairing-1",
        connectorDeviceId = "connector-1",
        connectorPublicKey = "public-key",
        connectorPublicKeyFingerprint = "public-key-fingerprint",
        oneTimeToken = "one-time-token",
        issuedAt = 100,
        expiresAt = 200,
    ),
    endpoint = "https://127.0.0.1:8443",
    tlsCertificateSha256 = "ab".repeat(32),
)
