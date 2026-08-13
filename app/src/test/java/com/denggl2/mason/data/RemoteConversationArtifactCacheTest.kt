package com.denggl2.mason.data

import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteConversationArtifactCacheTest {
    @Test
    fun samePathAndSizeReplacesStaleContent() = runBlocking {
        val directory = Files.createTempDirectory("mason-remote-preview-test").toFile()
        val target = directory.resolve("preview.txt")
        val firstBytes = "first".toByteArray()
        val updatedBytes = "later".toByteArray()
        try {
            ByteArrayInputStream(firstBytes).use { input ->
                replaceRemoteConversationCacheFile(
                    target = target,
                    input = input,
                    expectedBytes = firstBytes.size.toLong(),
                    maxBytes = 1024,
                )
            }
            ByteArrayInputStream(updatedBytes).use { input ->
                replaceRemoteConversationCacheFile(
                    target = target,
                    input = input,
                    expectedBytes = updatedBytes.size.toLong(),
                    maxBytes = 1024,
                )
            }

            assertEquals(firstBytes.size.toLong(), target.length())
            assertArrayEquals(updatedBytes, target.readBytes())
        } finally {
            target.delete()
            directory.delete()
        }
    }
}
