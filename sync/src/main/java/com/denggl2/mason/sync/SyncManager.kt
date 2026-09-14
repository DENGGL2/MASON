package com.denggl2.mason.sync

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.denggl2.mason.protocol.CommandEnvelope
import com.denggl2.mason.protocol.CommandResult
import com.denggl2.mason.protocol.CommandType
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.sync.data.AppDatabase
import com.denggl2.mason.sync.data.GlobalIdGenerator
import com.denggl2.mason.sync.data.entity.Conversation
import com.denggl2.mason.sync.data.entity.ConversationSyncModes
import com.denggl2.mason.sync.data.entity.LocalDevice
import com.denggl2.mason.sync.data.entity.Message
import com.denggl2.mason.sync.data.entity.MessageSyncStates
import com.denggl2.mason.sync.data.entity.SyncCursorEntity
import com.denggl2.mason.sync.data.entity.SyncOutboxEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ConversationExport(
    val conversation: ConversationData,
    val messages: List<MessageData>,
)

@Serializable
data class ConversationData(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val globalId: String? = null,
    val syncMode: String? = null,
    val authorityDeviceId: String? = null,
)

@Serializable
data class MessageData(
    val id: Long,
    val role: String,
    val content: String?,
    val toolCallId: String? = null,
    val toolCallName: String? = null,
    val timestamp: Long,
    val eventId: String? = null,
    val sourceDeviceId: String? = null,
    val syncState: String? = null,
)

@Serializable
data class ExportPayload(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val conversations: List<ConversationExport>,
)

fun interface SyncCommandSender {
    suspend fun send(command: CommandEnvelope): CommandResult
}

data class OutboxFlushSummary(
    val attempted: Int,
    val completed: Int,
    val failed: Int,
)

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val localDeviceDao = database.localDeviceDao()
    private val outboxDao = database.syncOutboxDao()
    private val cursorDao = database.syncCursorDao()
    private val outboxMutex = Mutex()

    /**
     * Create or reuse an existing conversation and return its id.
     * Uses the user's first message content as the title.
     */
    suspend fun createOrGetConversation(
        title: String,
        syncMode: String = ConversationSyncModes.LOCAL_ONLY,
    ): Long {
        require(syncMode in supportedSyncModes) { "Unsupported conversation sync mode: $syncMode" }
        val device = ensureLocalDevice()
        val conversation = Conversation(
            globalId = GlobalIdGenerator.newId(),
            title = title,
            syncMode = syncMode,
            authorityDeviceId = device.deviceId,
        )
        return conversationDao.insert(conversation)
    }

    /**
     * Save a single message associated with a conversation.
     */
    suspend fun saveMessage(
        conversationId: Long,
        role: String,
        content: String?,
        toolCallId: String? = null,
        toolCallName: String? = null,
    ): Long {
        val device = ensureLocalDevice()
        val now = System.currentTimeMillis()
        val eventId = GlobalIdGenerator.newId(now)
        return database.withTransaction {
            val conversation = conversationDao.getById(conversationId)
                ?: error("Conversation not found: $conversationId")
            val shouldQueue = role == "user" && conversation.syncMode == ConversationSyncModes.SHARED
            val messageId = messageDao.insert(
                Message(
                    conversationId = conversationId,
                    eventId = eventId,
                    sourceDeviceId = device.deviceId,
                    syncState = if (shouldQueue) MessageSyncStates.PENDING else MessageSyncStates.LOCAL_ONLY,
                    role = role,
                    content = content,
                    toolCallId = toolCallId,
                    toolCallName = toolCallName,
                    timestamp = now,
                ),
            )
            if (shouldQueue) {
                val command = CommandEnvelope(
                    commandId = eventId,
                    deviceId = device.deviceId,
                    issuedAt = now,
                    expiresAt = now + OUTBOX_COMMAND_TTL_MILLIS,
                    type = CommandType.EXECUTION_START,
                    payload = buildJsonObject {
                        put("conversationId", conversation.globalId)
                        put("eventId", eventId)
                        put("text", content.orEmpty())
                    },
                )
                outboxDao.insert(
                    SyncOutboxEntry(
                        commandId = command.commandId,
                        envelopeJson = MasonProtocolJson.encode(command),
                        nextAttemptAt = now,
                        createdAt = now,
                    ),
                )
            }
            conversationDao.update(conversation.copy(updatedAt = now))
            messageId
        }
    }

    fun getConversationsFlow(): Flow<List<Conversation>> = conversationDao.getAll()

    suspend fun getConversationsSnapshot(): List<Conversation> = conversationDao.getAllList()

    fun getMessagesFlow(conversationId: Long): Flow<List<Message>> =
        messageDao.getByConversation(conversationId)

    suspend fun getMessagesSnapshot(conversationId: Long): List<Message> =
        messageDao.getByConversationList(conversationId)

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteById(id)
    }

    suspend fun updateConversationTitle(id: Long, title: String) {
        conversationDao.getById(id)?.let { conv ->
            conversationDao.update(conv.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun getConversationTitle(id: Long): String? {
        return conversationDao.getById(id)?.title
    }

    suspend fun getLastMessage(conversationId: Long): Message? {
        return messageDao.getLastMessage(conversationId)
    }

    suspend fun getLocalDeviceId(): String = ensureLocalDevice().deviceId

    suspend fun updateConversationSyncMode(id: Long, syncMode: String) {
        require(syncMode in supportedSyncModes) { "Unsupported conversation sync mode: $syncMode" }
        conversationDao.getById(id)?.let { conversation ->
            conversationDao.update(
                conversation.copy(syncMode = syncMode, updatedAt = System.currentTimeMillis()),
            )
        }
    }

    suspend fun flushOutbox(
        sender: SyncCommandSender,
        now: Long = System.currentTimeMillis(),
        limit: Int = 50,
    ): OutboxFlushSummary = outboxMutex.withLock {
        require(limit > 0) { "Outbox limit must be positive" }
        outboxDao.requeueInterrupted(now)
        val ready = outboxDao.getReady(now, limit)
        var completed = 0
        var failed = 0
        ready.forEach { entry ->
            if (outboxDao.markInFlight(entry.id) == 0) return@forEach
            runCatching {
                val command = MasonProtocolJson.decode<CommandEnvelope>(entry.envelopeJson)
                val result = sender.send(command)
                require(result.commandId == command.commandId) {
                    "Command result ID does not match outbox command"
                }
                database.withTransaction {
                    check(outboxDao.markCompleted(entry.id, result.completedAt) == 1)
                    messageDao.updateSyncState(
                        eventId = command.commandId,
                        syncState = if (result.accepted) MessageSyncStates.SYNCED else MessageSyncStates.FAILED,
                    )
                }
            }.onSuccess {
                completed++
            }.onFailure { error ->
                val nextAttemptAt = now + retryDelayMillis(entry.attemptCount + 1)
                outboxDao.markFailed(
                    id = entry.id,
                    nextAttemptAt = nextAttemptAt,
                    error = error.message ?: error::class.simpleName.orEmpty(),
                )
                failed++
            }
        }
        OutboxFlushSummary(
            attempted = completed + failed,
            completed = completed,
            failed = failed,
        )
    }

    suspend fun getPendingOutboxCount(): Int = outboxDao.pendingCount()

    suspend fun advanceSyncCursor(
        conversationGlobalId: String,
        sourceDeviceId: String,
        sequence: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ): Long = database.withTransaction {
        require(sequence >= 0) { "Cursor sequence cannot be negative" }
        val advanced = cursorDao.advance(
            conversationGlobalId,
            sourceDeviceId,
            sequence,
            updatedAt,
        )
        if (advanced == 0 && cursorDao.get(conversationGlobalId, sourceDeviceId) == null) {
            cursorDao.insert(
                SyncCursorEntity(
                    conversationGlobalId = conversationGlobalId,
                    sourceDeviceId = sourceDeviceId,
                    sequence = sequence,
                    updatedAt = updatedAt,
                ),
            )
        }
        cursorDao.get(conversationGlobalId, sourceDeviceId)?.sequence
            ?: error("Failed to persist sync cursor")
    }

    suspend fun getSyncCursor(conversationGlobalId: String, sourceDeviceId: String): Long? =
        cursorDao.get(conversationGlobalId, sourceDeviceId)?.sequence

    suspend fun getConversationsSnapshotCount(): Int =
        conversationDao.getAllList().size

    suspend fun getMessagesSnapshotCount(): Int =
        messageDao.getAll().size

    /**
     * Export all conversations and messages as a JSON string.
     */
    suspend fun exportAll(): String {
        return json.encodeToString(exportPayload())
    }

    suspend fun exportMarkdown(conversationIds: Set<Long>? = null): String {
        val payload = exportPayload(conversationIds)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val jsonBlock = json.encodeToString(payload)

        return buildString {
            appendLine("# Mason 对话备份")
            appendLine()
            appendLine("- 导出时间：${dateFormat.format(Date(payload.exportedAt))}")
            appendLine("- 对话数量：${payload.conversations.size}")
            appendLine()

            payload.conversations.forEachIndexed { index, entry ->
                appendLine("## ${index + 1}. ${entry.conversation.title}")
                appendLine()
                appendLine("- 创建：${dateFormat.format(Date(entry.conversation.createdAt))}")
                appendLine("- 更新：${dateFormat.format(Date(entry.conversation.updatedAt))}")
                appendLine()

                entry.messages.forEach { message ->
                    val role = when (message.role) {
                        "user" -> "用户"
                        "assistant" -> "Mason"
                        "tool" -> "工具"
                        else -> message.role
                    }
                    appendLine("### $role · ${dateFormat.format(Date(message.timestamp))}")
                    appendLine()
                    appendLine(message.content?.ifBlank { "(空内容)" } ?: "(空内容)")
                    appendLine()
                }
            }

            appendLine("---")
            appendLine()
            appendLine("以下备份块用于 Mason 导入，请不要手动修改。")
            appendLine()
            appendLine("```mason-backup-json")
            appendLine(jsonBlock)
            appendLine("```")
        }
    }

    private suspend fun exportPayload(conversationIds: Set<Long>? = null): ExportPayload {
        val conversations = conversationDao.getAllList()
            .filter { conversationIds == null || it.id in conversationIds }
        val exportList = conversations.map { conv ->
            val messages = messageDao.getByConversationList(conv.id).map { msg ->
                MessageData(
                    id = msg.id,
                    role = msg.role,
                    content = msg.content,
                    toolCallId = msg.toolCallId,
                    toolCallName = msg.toolCallName,
                    timestamp = msg.timestamp,
                    eventId = msg.eventId,
                    sourceDeviceId = msg.sourceDeviceId,
                    syncState = msg.syncState,
                )
            }
            ConversationExport(
                conversation = ConversationData(
                    id = conv.id,
                    title = conv.title,
                    createdAt = conv.createdAt,
                    updatedAt = conv.updatedAt,
                    globalId = conv.globalId,
                    syncMode = conv.syncMode,
                    authorityDeviceId = conv.authorityDeviceId,
                ),
                messages = messages,
            )
        }
        return ExportPayload(conversations = exportList)
    }

    /**
     * Export all conversations and save to a file.
     */
    suspend fun exportMarkdownToFile(outputPath: java.io.File): Boolean {
        return try {
            outputPath.writeText(exportMarkdown())
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportMarkdownToFile(
        outputPath: java.io.File,
        conversationIds: Set<Long>,
    ): Boolean {
        return try {
            outputPath.writeText(exportMarkdown(conversationIds))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all conversations and messages.
     */
    suspend fun clearAll() {
        messageDao.deleteAll()
        conversationDao.deleteAll()
    }

    suspend fun deleteConversations(ids: Set<Long>) {
        ids.forEach { conversationDao.deleteById(it) }
    }

    /**
     * Import conversations from a JSON string.
     */
    suspend fun importFromJson(jsonStr: String): Int {
        return try {
            val payload = json.decodeFromString<ExportPayload>(jsonStr)
            var imported = 0
            for (entry in payload.conversations) {
                val device = ensureLocalDevice()
                val importedGlobalId = entry.conversation.globalId
                    ?.takeIf { conversationDao.getByGlobalId(it) == null }
                    ?: GlobalIdGenerator.newId(entry.conversation.createdAt.coerceAtLeast(0))
                val conv = Conversation(
                    globalId = importedGlobalId,
                    title = entry.conversation.title,
                    syncMode = entry.conversation.syncMode
                        ?.takeIf(supportedSyncModes::contains)
                        ?: ConversationSyncModes.LOCAL_ONLY,
                    authorityDeviceId = entry.conversation.authorityDeviceId ?: device.deviceId,
                    createdAt = entry.conversation.createdAt,
                    updatedAt = entry.conversation.updatedAt,
                )
                val convId = conversationDao.insert(conv)
                val messages = entry.messages.map { msg ->
                    val importedEventId = msg.eventId
                        ?.takeIf { messageDao.getByEventId(it) == null }
                        ?: GlobalIdGenerator.newId(msg.timestamp.coerceAtLeast(0))
                    Message(
                        conversationId = convId,
                        eventId = importedEventId,
                        sourceDeviceId = msg.sourceDeviceId ?: device.deviceId,
                        syncState = msg.syncState ?: MessageSyncStates.LOCAL_ONLY,
                        role = msg.role,
                        content = msg.content,
                        toolCallId = msg.toolCallId,
                        toolCallName = msg.toolCallName,
                        timestamp = msg.timestamp,
                    )
                }
                messageDao.insertAll(messages)
                imported++
            }
            imported
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun importFromMarkdown(markdown: String): Int {
        val blockRegex = Regex(
            pattern = "```mason-backup-json\\s*([\\s\\S]*?)\\s*```",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        val jsonBlock = blockRegex.find(markdown)?.groupValues?.getOrNull(1)
        return importFromJson(jsonBlock ?: markdown)
    }

    /**
     * Import conversations from a file URI.
     */
    suspend fun importFromUri(uri: Uri): Int {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val content = reader.use { it.readText() }
        return importFromMarkdown(content)
    }

    private suspend fun ensureLocalDevice(): LocalDevice = database.withTransaction {
        localDeviceDao.get()?.let { return@withTransaction it }
        val candidate = LocalDevice(
            deviceId = GlobalIdGenerator.newId(),
            createdAt = System.currentTimeMillis(),
        )
        localDeviceDao.insert(candidate)
        localDeviceDao.get() ?: error("Failed to create local device identity")
    }

    private fun retryDelayMillis(attemptCount: Int): Long {
        val exponent = (attemptCount - 1).coerceIn(0, 6)
        return BASE_RETRY_DELAY_MILLIS * (1L shl exponent)
    }

    private companion object {
        const val OUTBOX_COMMAND_TTL_MILLIS = 30L * 24 * 60 * 60 * 1_000
        const val BASE_RETRY_DELAY_MILLIS = 5_000L
        val supportedSyncModes = setOf(
            ConversationSyncModes.LOCAL_ONLY,
            ConversationSyncModes.SHARED,
            ConversationSyncModes.REMOTE_MIRROR,
        )
    }
}
