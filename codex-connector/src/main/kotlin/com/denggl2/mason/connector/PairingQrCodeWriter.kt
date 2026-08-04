package com.denggl2.mason.connector

import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingBootstrap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.file.Files
import java.nio.file.Path

object PairingQrCodeWriter {
    fun write(bootstrap: PairingBootstrap, outputPath: Path): Path {
        val output = outputPath.toAbsolutePath().normalize()
        require(output.fileName.toString().endsWith(".png", ignoreCase = true)) {
            "Pairing QR output must be a PNG file"
        }
        require(!Files.exists(output)) { "Pairing QR output already exists: $output" }
        Files.createDirectories(requireNotNull(output.parent) { "Pairing QR output needs a parent directory" })
        val payload = MasonProtocolJson.encode(bootstrap)
        val matrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            QR_CODE_SIZE,
            QR_CODE_SIZE,
            mapOf(
                EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to QR_CODE_MARGIN,
            ),
        )
        MatrixToImageWriter.writeToPath(matrix, "PNG", output)
        check(Files.isRegularFile(output) && Files.size(output) > 0) {
            "Pairing QR image was not written: $output"
        }
        return output
    }

    private const val QR_CODE_SIZE = 640
    private const val QR_CODE_MARGIN = 2
}
