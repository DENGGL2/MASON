package com.denggl2.mason.agent

import android.content.Context
import com.denggl2.mason.tool.ToolExecutor
import com.denggl2.mason.tool.ToolResult
import com.denggl2.mason.integration.A2aToolManager
import com.denggl2.mason.tool.INTERNAL_CONVERSATION_ID
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ToolExecutionSource {
    Chat,
    Agent,
    Skill,
    Automation,
    System,
}

data class ToolExecutionContext(
    val source: ToolExecutionSource,
    val taskRunId: String? = null,
    val conversationId: String? = null,
    val userConfirmed: Boolean = false,
    val background: Boolean = false,
)

data class ToolSecurityProfile(
    val name: String,
    val risk: ToolRiskLevel,
    val permissions: List<String>,
    val backgroundAllowed: Boolean,
    val mandatoryApproval: Boolean,
    val persistentGrantAllowed: Boolean,
)

@Serializable
data class ToolAuditRecord(
    val id: String,
    val toolName: String,
    val source: String,
    val taskRunId: String? = null,
    val risk: String,
    val success: Boolean,
    val message: String,
    val startedAt: Long,
    val finishedAt: Long,
)

@Singleton
class ToolGrantStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("mason_tool_grants", Context.MODE_PRIVATE)

    fun isAlwaysAllowed(toolName: String): Boolean =
        preferences.getStringSet(KEY_ALLOWED, emptySet()).orEmpty().contains(toolName)

    fun listAlwaysAllowed(): Set<String> =
        preferences.getStringSet(KEY_ALLOWED, emptySet()).orEmpty().toSortedSet()

    fun allowAlways(toolName: String) {
        val next = preferences.getStringSet(KEY_ALLOWED, emptySet()).orEmpty() + toolName
        preferences.edit().putStringSet(KEY_ALLOWED, next).apply()
    }

    fun revoke(toolName: String) {
        val next = preferences.getStringSet(KEY_ALLOWED, emptySet()).orEmpty() - toolName
        preferences.edit().putStringSet(KEY_ALLOWED, next).apply()
    }

    private companion object {
        const val KEY_ALLOWED = "always_allowed_tools"
    }
}

@Singleton
class ToolAuditStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun append(record: ToolAuditRecord) = withContext(Dispatchers.IO) {
        val records = readInternal().takeLast(MAX_RECORDS - 1) + record
        auditFile().writeText(
            json.encodeToString(ListSerializer(ToolAuditRecord.serializer()), records),
            Charsets.UTF_8,
        )
    }

    suspend fun list(): List<ToolAuditRecord> = withContext(Dispatchers.IO) { readInternal() }

    private fun readInternal(): List<ToolAuditRecord> {
        val file = auditFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ToolAuditRecord.serializer()), file.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    private fun auditFile(): File = File(context.filesDir, "audit/tool-executions.json").also {
        it.parentFile?.mkdirs()
    }

    private companion object {
        const val MAX_RECORDS = 500
    }
}

@Singleton
class GovernedToolExecutor @Inject constructor(
    private val executor: ToolExecutor,
    private val grants: ToolGrantStore,
    private val auditStore: ToolAuditStore,
) {
    suspend fun execute(
        name: String,
        args: Map<String, String>,
        context: ToolExecutionContext,
    ): ToolResult {
        val startedAt = System.currentTimeMillis()
        val profile = profile(name)
        val allowed = if (profile.mandatoryApproval) {
            context.userConfirmed
        } else {
            profile.risk == ToolRiskLevel.Low ||
                context.userConfirmed ||
                grants.isAlwaysAllowed(name) ||
                context.source in trustedSources
        }
        val result = if (!allowed) {
            ToolResult(success = false, error = "工具需要用户确认：$name")
        } else if (context.background && !profile.backgroundAllowed) {
            ToolResult(success = false, error = "工具不允许后台自动执行：$name")
        } else {
            val executionArgs = buildMap {
                putAll(args)
                if (name.startsWith("a2a__") && context.taskRunId != null) {
                    put(A2aToolManager.MASON_TASK_RUN_ID, context.taskRunId)
                }
                if (name in memoryWriteTools && context.conversationId != null) {
                    put(INTERNAL_CONVERSATION_ID, context.conversationId)
                }
            }
            executor.execute(name, executionArgs)
        }
        auditStore.append(
            ToolAuditRecord(
                id = UUID.randomUUID().toString(),
                toolName = name,
                source = context.source.name,
                taskRunId = context.taskRunId,
                risk = profile.risk.name,
                success = result.success,
                message = result.error ?: result.data.entries.joinToString { "${it.key}=${it.value}" }.take(500),
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
            ),
        )
        return result
    }

    fun profile(name: String): ToolSecurityProfile = ToolPolicy.profileFor(name)

    private companion object {
        val trustedSources = setOf(ToolExecutionSource.System)
        val memoryWriteTools = setOf("memory_save", "memory_save_sensitive")
    }
}
