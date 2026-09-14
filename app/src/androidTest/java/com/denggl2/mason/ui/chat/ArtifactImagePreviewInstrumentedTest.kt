package com.denggl2.mason.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.denggl2.mason.data.ArtifactMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtifactImagePreviewInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun commonRasterFormatsPassValidationAndPlatformDecode() {
        val source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.RED)
            setPixel(1, 0, Color.GREEN)
            setPixel(0, 1, Color.BLUE)
            setPixel(1, 1, Color.WHITE)
        }
        try {
            verifyRaster("png", "image/png", source.compressToBytes(Bitmap.CompressFormat.PNG))
            verifyRaster("jpg", "image/jpeg", source.compressToBytes(Bitmap.CompressFormat.JPEG))
            verifyRaster("webp", "image/webp", source.compressToBytes(Bitmap.CompressFormat.WEBP))
            verifyRaster("gif", "image/gif", ONE_PIXEL_GIF)
            verifyRaster("bmp", "image/x-ms-bmp", createBmpFixture())
            verifyRaster("ico", "image/vnd.microsoft.icon", createIcoFixture(source))
        } finally {
            source.recycle()
        }
    }

    @Test
    fun utf16SvgPassesValidationAndSecureNormalization() {
        val bytes = (
            "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-16\"?>" +
                "<!-- first --><!-- second -->" +
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"12\">" +
                "<rect width=\"16\" height=\"12\" fill=\"#ff0000\"/>" +
                "</svg>"
            ).toByteArray(Charsets.UTF_16LE)
        val file = writeFixture("svg", bytes)
        try {
            val validated = validateImageArtifact(context, file.toArtifact("image/svg+xml")).getOrThrow()
            assertEquals("image/svg+xml", validated.mimeType)
            val normalized = normalizeSvgForPreview(bytes)
            assertTrue(normalized.contains("viewBox=\"0 0 16 12\""))
        } finally {
            file.delete()
        }
    }

    @Test
    fun remotePreviewCacheImagePassesValidationAfterPathNormalization() {
        val directory = File(context.filesDir, "remote-previews/cache-key")
        assertTrue(directory.exists() || directory.mkdirs())
        val file = File(directory, "preview.png").apply { writeBytes(ONE_PIXEL_PNG) }
        try {
            val pathWithParentSegment = File(directory, "../cache-key/${file.name}")
            val validated = validateImageArtifact(
                context,
                file.toArtifact("image/png").copy(path = pathWithParentSegment.absolutePath),
            ).getOrThrow()

            assertEquals(file.canonicalFile, validated.file)
            assertEquals("image/png", validated.mimeType)
        } finally {
            file.delete()
            directory.delete()
        }
    }

    private fun verifyRaster(extension: String, declaredMimeType: String, bytes: ByteArray) {
        val file = writeFixture(extension, bytes)
        try {
            val validated = validateImageArtifact(context, file.toArtifact(declaredMimeType)).getOrThrow()
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(validated.file)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                assertTrue("$extension width", bitmap.width > 0)
                assertTrue("$extension height", bitmap.height > 0)
            } finally {
                bitmap.recycle()
            }
        } finally {
            file.delete()
        }
    }

    private fun writeFixture(extension: String, bytes: ByteArray): File {
        val directory = File(context.filesDir, "artifacts")
        assertTrue(directory.exists() || directory.mkdirs())
        return File(directory, "preview-device-${System.nanoTime()}.$extension").apply {
            writeBytes(bytes)
        }
    }

    private fun File.toArtifact(mimeType: String): ArtifactMetadata = ArtifactMetadata(
        name = name,
        path = absolutePath,
        mimeType = mimeType,
        bytes = length(),
        createdAt = System.currentTimeMillis(),
    )

    private fun Bitmap.compressToBytes(format: Bitmap.CompressFormat): ByteArray =
        ByteArrayOutputStream().use { output ->
            assertTrue(compress(format, 90, output))
            output.toByteArray()
        }

    private fun createIcoFixture(source: Bitmap): ByteArray {
        val png = source.compressToBytes(Bitmap.CompressFormat.PNG)
        return ByteBuffer.allocate(22 + png.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0.toShort())
            putShort(1.toShort())
            putShort(1.toShort())
            put(source.width.toByte())
            put(source.height.toByte())
            put(0.toByte())
            put(0.toByte())
            putShort(1.toShort())
            putShort(32.toShort())
            putInt(png.size)
            putInt(22)
            put(png)
        }.array()
    }

    private fun createBmpFixture(): ByteArray =
        ByteBuffer.allocate(70).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte())
            put('M'.code.toByte())
            putInt(70)
            putInt(0)
            putInt(54)
            putInt(40)
            putInt(2)
            putInt(2)
            putShort(1.toShort())
            putShort(24.toShort())
            putInt(0)
            putInt(16)
            putInt(0)
            putInt(0)
            putInt(0)
            putInt(0)
            put(byteArrayOf(0, 0, -1, 0, -1, 0, 0, 0))
            put(byteArrayOf(-1, 0, 0, -1, -1, -1, 0, 0))
        }.array()

    private companion object {
        val ONE_PIXEL_PNG: ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )
        val ONE_PIXEL_GIF: ByteArray = Base64.decode(
            "R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==",
            Base64.DEFAULT,
        )
    }
}
