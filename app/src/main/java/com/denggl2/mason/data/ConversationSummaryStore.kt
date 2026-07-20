package com.denggl2.mason.data

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ConversationSummary(
    val scopeId: String,
    val content: String,
    val coveredMessageCount: Int,
    val coveredThroughTimestamp: Long?,
    val sourceFingerprint: String,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Singleton
class ConversationSummaryStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root = File(context.filesDir, "conversation_summaries")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun get(scopeId: String): ConversationSummary? = withContext(Dispatchers.IO) {
        if (!scopeId.isValidSummaryScopeId()) return@withContext null
        mutex.withLock {
            val file = summaryFile(scopeId)
            if (!file.isFile) return@withLock null
            runCatching { json.decodeFromString<ConversationSummary>(file.readText(Charsets.UTF_8)) }.getOrNull()
        }
    }

    suspend fun save(summary: ConversationSummary): ConversationSummary = withContext(Dispatchers.IO) {
        require(summary.scopeId.isValidSummaryScopeId()) { "Invalid conversation summary scope ID" }
        mutex.withLock {
            root.mkdirs()
            val atomicFile = AtomicFile(summaryFile(summary.scopeId))
            val output = atomicFile.startWrite()
            try {
                output.write(json.encodeToString(summary).toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(output)
                summary
            } catch (error: Throwable) {
                atomicFile.failWrite(output)
                throw error
            }
        }
    }

    private fun summaryFile(scopeId: String): File = File(root, "$scopeId.json")
}

private fun String.isValidSummaryScopeId(): Boolean = matches(Regex("[A-Za-z0-9._-]{1,100}"))
