package com.denggl2.mason.connector

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexExecutableLocatorTest {
    @Test
    fun `bundled locator skips incomplete newest installation`() {
        val root = Files.createTempDirectory("mason-codex-locator")
        val binRoot = Files.createDirectories(root.resolve("OpenAI").resolve("Codex").resolve("bin"))
        val complete = Files.createDirectories(binRoot.resolve("complete"))
        val incomplete = Files.createDirectories(binRoot.resolve("incomplete"))
        val completeExecutable = Files.createFile(complete.resolve("codex.exe"))
        Files.createFile(complete.resolve("codex-code-mode-host.exe"))
        val incompleteExecutable = Files.createFile(incomplete.resolve("codex.exe"))
        Files.setLastModifiedTime(completeExecutable, FileTime.fromMillis(1L))
        Files.setLastModifiedTime(incompleteExecutable, FileTime.fromMillis(2L))

        val located = CodexExecutableLocator.locate(
            environment = mapOf(
                "LOCALAPPDATA" to root.toString(),
                "PATH" to "",
            ),
        )

        assertEquals(completeExecutable, located)
    }
}
