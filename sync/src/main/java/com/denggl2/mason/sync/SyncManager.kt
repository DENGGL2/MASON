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

    /**
     * Export all conversations and messages as a JSON string.
     */
    suspend fun exportAll(): String {
        val conversations = conversationDao.getAllList()
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
        return json.encodeToString(ExportPayload(conversations = exportList))
    }

    /**
     * Export all conversations and save to a file.
     */
    suspend fun exportToFile(outputPath: java.io.File): Boolean {
        return try {
            val jsonStr = exportAll()
            outputPath.writeText(jsonStr)
            true
        } catch (e: Exception) {
            false
        }
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

    /**
     * Import conversations from a file URI.
     */
    suspend fun importFromUri(uri: Uri): Int {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonStr = reader.use { it.readText() }
        return importFromJson(jsonStr)
    }
}
