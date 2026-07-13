package com.denggl2.mason.sync

import android.content.Context
import android.net.Uri
import com.denggl2.mason.sync.data.AppDatabase
import com.denggl2.mason.sync.data.entity.Conversation
import com.denggl2.mason.sync.data.entity.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
)

@Serializable
data class MessageData(
    val id: Long,
    val role: String,
    val content: String?,
    val toolCallId: String? = null,
    val toolCallName: String? = null,
    val timestamp: Long,
)

@Serializable
data class ExportPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val conversations: List<ConversationExport>,
)

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()

    /**
     * Create or reuse an existing conversation and return its id.
     * Uses the user's first message content as the title.
     */
    suspend fun createOrGetConversation(title: String): Long {
        val conversation = Conversation(title = title)
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
    ) {
        messageDao.insert(
            Message(
                conversationId = conversationId,
                role = role,
                content = content,
                toolCallId = toolCallId,
                toolCallName = toolCallName,
            )
        )
        conversationDao.getById(conversationId)?.let { conv ->
            conversationDao.update(conv.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun getConversationsFlow(): Flow<List<Conversation>> = conversationDao.getAll()

    fun getMessagesFlow(conversationId: Long): Flow<List<Message>> =
        messageDao.getByConversation(conversationId)

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
                )
            }
            ConversationExport(
                conversation = ConversationData(
                    id = conv.id,
                    title = conv.title,
                    createdAt = conv.createdAt,
                    updatedAt = conv.updatedAt,
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
                val conv = Conversation(
                    title = entry.conversation.title,
                    createdAt = entry.conversation.createdAt,
                    updatedAt = entry.conversation.updatedAt,
                )
                val convId = conversationDao.insert(conv)
                val messages = entry.messages.map { msg ->
                    Message(
                        conversationId = convId,
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
}
