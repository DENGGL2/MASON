package com.denggl2.mason.connector

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

object CodexExecutableLocator {
    fun locate(environment: Map<String, String> = System.getenv()): Path? {
        environment["MASON_CODEX_PATH"]?.let(Path::of)?.takeIf(::isUsable)?.let { return it }

        val localAppData = environment["LOCALAPPDATA"]
        if (!localAppData.isNullOrBlank()) {
            val binRoot = Path.of(localAppData, "OpenAI", "Codex", "bin")
            newestBundledExecutable(binRoot)?.let { return it }
        }

        val executableName = if (isWindows()) "codex.exe" else "codex"
        return environment["PATH"]
            .orEmpty()
            .split(System.getProperty("path.separator"))
            .asSequence()
            .filter(String::isNotBlank)
            .map { Path.of(it, executableName) }
            .filterNot { it.toString().contains("WindowsApps", ignoreCase = true) }
            .firstOrNull(::isUsable)
    }

    private fun newestBundledExecutable(binRoot: Path): Path? {
        if (!binRoot.exists()) return null
        return Files.list(binRoot).use { directories ->
            directories
                .filter(Files::isDirectory)
                .map { it.resolve(if (isWindows()) "codex.exe" else "codex") }
                .filter(::isUsable)
                .sorted { left, right ->
                    Files.getLastModifiedTime(right).compareTo(Files.getLastModifiedTime(left))
                }
                .findFirst()
                .orElse(null)
        }
    }

    private fun isUsable(path: Path): Boolean = path.isRegularFile()

    private fun isWindows(): Boolean = System.getProperty("os.name").contains("Windows", ignoreCase = true)
}
