package com.denggl2.mason.ui.chat

import com.denggl2.mason.data.ArtifactMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactImagePreviewLogicTest {

    @Test
    fun `supported image mime types are previewable`() {
        listOf(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif",
            "image/svg+xml",
            "image/bmp",
            "image/heif",
            "image/heic",
            "image/avif",
            "image/x-icon",
            "image/vnd.microsoft.icon",
            "image/jpg",
            "image/x-png",
            "image/x-ms-bmp",
        ).forEach { mimeType ->
            assertTrue(isPreviewableImageArtifact(artifact(name = "output.bin", mimeType = mimeType)))
        }
        assertTrue(isPreviewableImageArtifact(artifact(name = "output.bin", mimeType = "IMAGE/PNG; charset=binary")))
    }

    @Test
    fun `supported image extensions provide a mime fallback`() {
        listOf("png", "jpg", "jpeg", "webp", "gif", "svg", "bmp", "heif", "heic", "avif", "ico")
            .forEach { extension ->
            assertTrue(
                isPreviewableImageArtifact(
                    artifact(name = "preview.$extension", mimeType = "application/octet-stream"),
                ),
            )
        }
        assertTrue(isPreviewableImageArtifact(artifact(name = "PREVIEW.PNG", mimeType = "")))
    }

    @Test
    fun `unsupported artifacts are not previewable`() {
        assertFalse(isPreviewableImageArtifact(artifact(name = "notes.txt", mimeType = "text/plain")))
        assertFalse(isPreviewableImageArtifact(artifact(name = "vector.tiff", mimeType = "image/tiff")))
        assertFalse(isPreviewableImageArtifact(artifact(name = "photo.raw", mimeType = "image/x-canon-cr2")))
    }

    @Test
    fun `avif is not advertised when the platform decoder is unavailable`() {
        assertFalse(
            isPreviewableImageArtifact(
                artifact(name = "preview.avif", mimeType = "image/avif"),
                platformSupportsAvif = false,
            ),
        )
        assertTrue(
            isPreviewableImageArtifact(
                artifact(name = "preview.avif", mimeType = "image/avif"),
                platformSupportsAvif = true,
            ),
        )
    }

    @Test
    fun `svg images are identified independently from raster images`() {
        assertTrue(isSvgImageArtifact(artifact(name = "vector.bin", mimeType = "image/svg+xml")))
        assertTrue(isSvgImageArtifact(artifact(name = "vector.SVG", mimeType = "application/octet-stream")))
        assertFalse(isSvgImageArtifact(artifact(name = "photo.png", mimeType = "image/png")))
    }

    @Test
    fun `common image headers are verified instead of trusting extensions`() {
        val bmp = byteArrayOf('B'.code.toByte(), 'M'.code.toByte()) +
            ByteArray(8) + byteArrayOf(14, 0, 0, 0)
        val ico = byteArrayOf(0, 0, 1, 0, 1, 0)
        val heic = byteArrayOf(0, 0, 0, 16) + "ftypheic".toByteArray() + byteArrayOf(0, 0, 0, 0)
        val avif = byteArrayOf(0, 0, 0, 16) + "ftypavif".toByteArray() + byteArrayOf(0, 0, 0, 0)
        val svg = "  <?xml version=\"1.0\"?>\n<svg viewBox=\"0 0 10 10\"></svg>".toByteArray()

        assertEquals("image/bmp", detectPreviewableImageMimeType(bmp, "image/bmp"))
        assertEquals("image/x-icon", detectPreviewableImageMimeType(ico, "image/x-icon"))
        assertEquals("image/x-icon", detectPreviewableImageMimeType(ico, "image/vnd.microsoft.icon"))
        assertEquals("image/heic", detectPreviewableImageMimeType(heic, "image/heif"))
        assertEquals("image/avif", detectPreviewableImageMimeType(avif, "image/avif"))
        assertEquals("image/svg+xml", detectPreviewableImageMimeType(svg, "image/svg+xml"))
        assertEquals("image/svg+xml", detectPreviewableImageMimeType("<svg/>".toByteArray(), "*/*"))
        assertEquals(null, detectPreviewableImageMimeType("not an image".toByteArray(), "image/svg+xml"))
        assertEquals(null, detectPreviewableImageMimeType(bmp, "image/svg+xml"))
    }

    @Test
    fun `iso base media compatible brands identify avif and heif`() {
        val avif = byteArrayOf(0, 0, 0, 24) + "ftypmif1".toByteArray() +
            byteArrayOf(0, 0, 0, 0) + "avifmif1".toByteArray()
        val heic = byteArrayOf(0, 0, 0, 20) + "ftypmif1".toByteArray() +
            byteArrayOf(0, 0, 0, 0) + "heic".toByteArray()

        assertEquals("image/avif", detectPreviewableImageMimeType(avif, "image/avif"))
        assertEquals("image/heic", detectPreviewableImageMimeType(heic, "image/heif"))
    }

    @Test
    fun `svg detection accepts repeated prolog nodes and utf16`() {
        val utf8 = """<?xml version="1.0"?><!-- one --><!-- two --><?mason value?><svg/>"""
        val utf16 = "\uFEFF<!-- one --><svg xmlns=\"http://www.w3.org/2000/svg\"/>"

        assertEquals("image/svg+xml", detectPreviewableImageMimeType(utf8.toByteArray(), "image/svg+xml"))
        assertEquals(
            "image/svg+xml",
            detectPreviewableImageMimeType(utf16.toByteArray(Charsets.UTF_16LE), "image/svg+xml"),
        )
        assertTrue(normalizeSvgForPreview(utf16.toByteArray(Charsets.UTF_16LE)).contains("<svg"))
    }

    @Test
    fun `svg preview normalization fills the viewport and preserves aspect ratio`() {
        val normalized = normalizeSvgForPreview(
            """<svg xmlns="http://www.w3.org/2000/svg" width="256" height="128"><rect width="256" height="128"/></svg>""",
        )

        assertTrue(normalized.contains("width=\"256\""))
        assertTrue(normalized.contains("height=\"128\""))
        assertTrue(normalized.contains("viewBox=\"0 0 256 128\""))
        assertTrue(normalized.contains("preserveAspectRatio=\"xMidYMid meet\""))
    }

    @Test
    fun `svg preview document supplies a concrete centered viewport`() {
        val svg = """<svg viewBox="0 0 10 10"><circle cx="5" cy="5" r="4"/></svg>"""
        val document = buildSvgPreviewDocument(svg, viewportWidthCssPx = 357, viewportHeightCssPx = 228)

        assertTrue(document.contains("display: flex"))
        assertTrue(document.contains("align-items: center"))
        assertTrue(document.contains("width: 357px !important"))
        assertTrue(document.contains("height: 228px !important"))
        assertTrue(document.contains("<body>$svg</body>"))
        assertFalse(document.contains("data:image/svg+xml"))
    }

    @Test
    fun `svg preview document rejects an empty viewport`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildSvgPreviewDocument("<svg/>", viewportWidthCssPx = 0, viewportHeightCssPx = 228)
        }
    }

    @Test
    fun `svg preview normalization rejects non svg roots and doctypes`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeSvgForPreview("<html></html>")
        }
        assertThrows(Exception::class.java) {
            normalizeSvgForPreview("<!DOCTYPE svg><svg></svg>")
        }
        assertThrows(Exception::class.java) {
            normalizeSvgForPreview(
                """<!DOCTYPE svg [<!ENTITY secret SYSTEM "file:///etc/passwd">]><svg>&secret;</svg>""",
            )
        }
    }

    @Test
    fun `sample size is a power of two that keeps decoded edge within target`() {
        assertEquals(1, calculateArtifactPreviewSampleSize(width = 1_024, height = 768, maxEdge = 1_024))
        assertEquals(2, calculateArtifactPreviewSampleSize(width = 2_048, height = 1_024, maxEdge = 1_024))
        assertEquals(4, calculateArtifactPreviewSampleSize(width = 4_001, height = 2_000, maxEdge = 1_024))
    }

    @Test
    fun `invalid sample dimensions use no sampling`() {
        assertEquals(1, calculateArtifactPreviewSampleSize(width = 0, height = 100, maxEdge = 100))
        assertEquals(1, calculateArtifactPreviewSampleSize(width = 100, height = -1, maxEdge = 100))
        assertEquals(1, calculateArtifactPreviewSampleSize(width = 100, height = 100, maxEdge = 0))
    }

    @Test
    fun `one times scale centers landscape and portrait content`() {
        assertOffset(
            expectedX = 0f,
            expectedY = 0f,
            actual = clampArtifactImageOffset(80f, -40f, 1f, 300f, 500f, 600f, 300f),
        )
        assertOffset(
            expectedX = 0f,
            expectedY = 0f,
            actual = clampArtifactImageOffset(-80f, 40f, 1f, 500f, 300f, 300f, 600f),
        )
    }

    @Test
    fun `zoomed landscape content clamps both axes after fit`() {
        val clamped = clampArtifactImageOffset(
            offsetX = 500f,
            offsetY = -500f,
            scale = 4f,
            viewportWidth = 300f,
            viewportHeight = 500f,
            contentWidth = 600f,
            contentHeight = 300f,
        )

        assertOffset(expectedX = 450f, expectedY = -50f, actual = clamped)
    }

    @Test
    fun `zoomed portrait content clamps both axes after fit`() {
        val clamped = clampArtifactImageOffset(
            offsetX = -500f,
            offsetY = 500f,
            scale = 4f,
            viewportWidth = 500f,
            viewportHeight = 300f,
            contentWidth = 300f,
            contentHeight = 600f,
        )

        assertOffset(expectedX = -50f, expectedY = 450f, actual = clamped)
    }

    @Test
    fun `offsets within zoom bounds are preserved`() {
        val clamped = clampArtifactImageOffset(
            offsetX = 40f,
            offsetY = -20f,
            scale = 2f,
            viewportWidth = 300f,
            viewportHeight = 300f,
            contentWidth = 300f,
            contentHeight = 300f,
        )

        assertOffset(expectedX = 40f, expectedY = -20f, actual = clamped)
    }

    @Test
    fun `invalid transform parameters reset the offset`() {
        assertOffset(0f, 0f, clampArtifactImageOffset(Float.NaN, 0f, 2f, 100f, 100f, 100f, 100f))
        assertOffset(0f, 0f, clampArtifactImageOffset(1f, 1f, Float.POSITIVE_INFINITY, 100f, 100f, 100f, 100f))
        assertOffset(0f, 0f, clampArtifactImageOffset(1f, 1f, 2f, 0f, 100f, 100f, 100f))
        assertOffset(0f, 0f, clampArtifactImageOffset(1f, 1f, 2f, 100f, 100f, -1f, 100f))
    }

    private fun artifact(name: String, mimeType: String) = ArtifactMetadata(
        name = name,
        path = name,
        mimeType = mimeType,
        bytes = 1L,
        createdAt = 1L,
    )

    private fun assertOffset(expectedX: Float, expectedY: Float, actual: ArtifactImageOffset) {
        assertEquals(expectedX, actual.x, 0.001f)
        assertEquals(expectedY, actual.y, 0.001f)
    }
}
