package com.denggl2.mason.agent

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class TaskRunStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val writeMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun save(run: TaskRun): TaskRun = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            require(run.id.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "TaskRun ID 格式不正确" }
            val snapshot = run.copy(updatedAt = System.currentTimeMillis())
            writeAtomically(runFile(run.id), json.encodeToString(snapshot))
            snapshot
        }
    }

    suspend fun get(id: String): TaskRun? = withContext(Dispatchers.IO) {
        if (!id.matches(Regex("[A-Za-z0-9._-]{1,100}"))) return@withContext null
        read(runFile(id))
    }

    suspend fun list(conversationId: Long? = null): List<TaskRun> = withContext(Dispatchers.IO) {
        root().listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull(::read)
            .filter { conversationId == null || it.conversationId == conversationId }
            .sortedByDescending(TaskRun::updatedAt)
    }

    suspend fun recoverLatest(conversationId: Long?): TaskRun? = withContext(Dispatchers.IO) {
        list(conversationId).firstOrNull { it.status in recoverableStatuses }?.let { run ->
            run.recoverAfterProcessRestart().also { save(it) }
        }
    }

    private fun read(file: File): TaskRun? = runCatching {
        json.decodeFromString<TaskRun>(file.readText(Charsets.UTF_8))
    }.getOrNull()

    private fun runFile(id: String): File = File(root(), "$id.json")

    private fun writeAtomically(file: File, content: String) {
        val atomicFile = AtomicFile(file)
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(atomicFile::failWrite)
            throw error
        }
    }

    private fun root(): File = File(context.filesDir, "task_runs").also { it.mkdirs() }

    private companion object {
        val recoverableStatuses = setOf(TaskRunStatus.Running, TaskRunStatus.WaitingForUser)
    }
}
