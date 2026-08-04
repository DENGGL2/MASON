package com.denggl2.mason.phoneagent

import android.content.Context
import com.denggl2.mason.tool.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class PhoneAgentLogStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<PhoneAgentLogEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    suspend fun refresh() = mutex.withLock {
        _entries.value = withContext(Dispatchers.IO) { readInternal() }
    }

    suspend fun append(
        action: String,
        summary: String,
        result: ToolResult,
        packageName: String?,
        snapshotVersion: Long?,
    ) = mutex.withLock {
        val entry = PhoneAgentLogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            action = action,
            summary = summary,
            success = result.success,
            error = result.error,
            packageName = packageName,
            snapshotVersion = snapshotVersion,
            details = result.data
                .filterKeys { it !in HIDDEN_DETAIL_KEYS }
                .mapValues { (_, value) -> value.take(MAX_DETAIL_LENGTH) },
        )
        val next = (_entries.value.ifEmpty { withContext(Dispatchers.IO) { readInternal() } } + entry)
            .takeLast(MAX_RECORDS)
        withContext(Dispatchers.IO) {
            val file = logFile()
            file.writeText(
                json.encodeToString(ListSerializer(PhoneAgentLogEntry.serializer()), next),
                Charsets.UTF_8,
            )
        }
        _entries.value = next
    }

    private fun readInternal(): List<PhoneAgentLogEntry> {
        val file = logFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(
                ListSerializer(PhoneAgentLogEntry.serializer()),
                file.readText(Charsets.UTF_8),
            )
        }.getOrDefault(emptyList())
    }

    private fun logFile(): File = File(context.filesDir, "phone-agent/activity.json").also {
        it.parentFile?.mkdirs()
    }

    private companion object {
        const val MAX_RECORDS = 200
        const val MAX_DETAIL_LENGTH = 500
        val HIDDEN_DETAIL_KEYS = setOf("snapshot_json", "node_id_rule", "model_visibility")
    }
}
