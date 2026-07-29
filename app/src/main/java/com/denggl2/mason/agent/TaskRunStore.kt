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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class TaskRunStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val writeMutex = Mutex()
    private val unreadPreferences = context.getSharedPreferences(UNREAD_PREFERENCES, Context.MODE_PRIVATE)
    private val _runs = MutableStateFlow<Map<String, TaskRun>>(emptyMap())
    val runs: StateFlow<Map<String, TaskRun>> = _runs.asStateFlow()
    private val _activeConversationIds = MutableStateFlow<Set<Long>>(emptySet())
    val activeConversationIds: StateFlow<Set<Long>> = _activeConversationIds.asStateFlow()
    private val _unreadCompletionConversationIds = MutableStateFlow(loadUnreadCompletionConversationIds())
    val unreadCompletionConversationIds: StateFlow<Set<Long>> = _unreadCompletionConversationIds.asStateFlow()
    private val _foregroundConversationIds = MutableStateFlow<Set<Long>>(emptySet())
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun save(run: TaskRun): TaskRun = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            require(run.id.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "TaskRun ID 格式不正确" }
            val previousStatus = _runs.value[run.id]?.status ?: read(runFile(run.id))?.status
            val snapshot = run.copy(updatedAt = System.currentTimeMillis())
            writeAtomically(runFile(run.id), json.encodeToString(snapshot))
            _runs.value = _runs.value + (snapshot.id to snapshot)
            snapshot.conversationId?.let { conversationId ->
                if (shouldMarkConversationCompletionUnread(
                        previousStatus = previousStatus,
                        nextStatus = snapshot.status,
                        isForeground = conversationId in _foregroundConversationIds.value,
                    )
                ) {
                    updateUnreadCompletionConversationIds { it + conversationId }
                }
            }
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

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val snapshots = root().listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull(::read)
            .associateBy(TaskRun::id)
        _runs.value = snapshots
    }

    suspend fun recoverLatest(conversationId: Long?): TaskRun? = withContext(Dispatchers.IO) {
        list(conversationId).firstOrNull { it.status in recoverableStatuses }?.let { run ->
            run.recoverAfterProcessRestart().also { save(it) }
        }
    }

    fun markConversationActive(conversationId: Long) {
        _activeConversationIds.update { it + conversationId }
    }

    fun markConversationInactive(conversationId: Long) {
        _activeConversationIds.update { it - conversationId }
    }

    fun markConversationForegrounded(conversationId: Long) {
        _foregroundConversationIds.update { it + conversationId }
        markConversationSeen(conversationId)
    }

    fun markConversationBackgrounded(conversationId: Long) {
        _foregroundConversationIds.update { it - conversationId }
    }

    fun markConversationSeen(conversationId: Long) {
        updateUnreadCompletionConversationIds { it - conversationId }
    }

    private fun read(file: File): TaskRun? = runCatching {
        json.decodeFromString<TaskRun>(file.readText(Charsets.UTF_8))
    }.getOrNull()

    private fun runFile(id: String): File = File(root(), "$id.json")

    private fun loadUnreadCompletionConversationIds(): Set<Long> = unreadPreferences
        .getStringSet(UNREAD_CONVERSATIONS_KEY, emptySet())
        .orEmpty()
        .mapNotNull(String::toLongOrNull)
        .toSet()

    @Synchronized
    private fun updateUnreadCompletionConversationIds(transform: (Set<Long>) -> Set<Long>) {
        val current = _unreadCompletionConversationIds.value
        val updated = transform(current)
        if (updated == current) return
        _unreadCompletionConversationIds.value = updated
        unreadPreferences.edit()
            .putStringSet(UNREAD_CONVERSATIONS_KEY, updated.map(Long::toString).toSet())
            .apply()
    }

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
        const val UNREAD_PREFERENCES = "task_run_completion_state"
        const val UNREAD_CONVERSATIONS_KEY = "unread_completion_conversation_ids"
    }
}

internal fun shouldMarkConversationCompletionUnread(
    previousStatus: TaskRunStatus?,
    nextStatus: TaskRunStatus,
    isForeground: Boolean,
): Boolean = !isForeground &&
    previousStatus in setOf(TaskRunStatus.Running, TaskRunStatus.WaitingForUser) &&
    nextStatus in setOf(TaskRunStatus.Completed, TaskRunStatus.Failed)
