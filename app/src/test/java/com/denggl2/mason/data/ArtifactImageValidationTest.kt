package com.denggl2.mason.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactImageValidationTest {
    @Test
    fun detectsSupportedImageBytesAndRejectsFakeContent() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        assertEquals("image/png", detectImageMimeType(png, "image/png"))
        assertEquals("image/png", detectImageMimeType(png, "application/octet-stream"))
        assertNull(detectImageMimeType("not an image".toByteArray(), "image/png"))
        assertNull(detectImageMimeType(png, "image/jpeg"))
    }

    @Test
    fun blocksLocalAndPrivateImageHosts() {
        assertTrue("localhost".isPrivateArtifactHost())
        assertTrue("127.0.0.1".isPrivateArtifactHost())
        assertTrue("192.168.1.10".isPrivateArtifactHost())
        assertTrue("172.20.1.5".isPrivateArtifactHost())
    }
}
