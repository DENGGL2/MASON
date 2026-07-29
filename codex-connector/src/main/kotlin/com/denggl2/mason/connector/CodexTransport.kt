package com.denggl2.mason.connector

import java.io.BufferedWriter
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface CodexTransport : AutoCloseable {
    val messages: Flow<JsonObject>

    suspend fun send(message: JsonObject)
}

class ProcessCodexTransport private constructor(
    private val process: Process,
    private val writer: BufferedWriter,
) : CodexTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incoming = Channel<JsonObject>(Channel.UNLIMITED)
    private val writeMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override val messages: Flow<JsonObject> = incoming.receiveAsFlow()

    init {
        scope.launch {
            runCatching {
                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.filter(String::isNotBlank).forEach { line ->
                        val parsed = json.parseToJsonElement(line) as? JsonObject ?: return@forEach
                        incoming.send(parsed)
                    }
                }
            }.onFailure(incoming::close)
            incoming.close()
        }
        scope.launch {
            process.errorStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { /* Drain stderr so the child process cannot block. */ }
            }
        }
    }

    override suspend fun send(message: JsonObject) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            writer.write(message.toString())
            writer.newLine()
            writer.flush()
        }
    }

    override fun close() {
        runCatching { writer.close() }
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        scope.cancel()
        incoming.close()
    }

    companion object {
        fun start(executable: Path, workingDirectory: Path): ProcessCodexTransport {
            val process = ProcessBuilder(
                executable.toString(),
                "app-server",
                "--listen",
                "stdio://",
            )
                .directory(workingDirectory.toFile())
                .start()
            return ProcessCodexTransport(
                process = process,
                writer = process.outputStream.bufferedWriter(Charsets.UTF_8),
            )
        }
    }
}
