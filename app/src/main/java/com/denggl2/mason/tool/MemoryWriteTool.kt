package com.denggl2.mason.tool

import com.denggl2.mason.data.MemorySaveStatus
import com.denggl2.mason.data.ProjectContext
import com.denggl2.mason.data.ProjectContextStore
import com.denggl2.mason.data.UserMemoryScope
import com.denggl2.mason.data.UserMemoryStore
import com.denggl2.mason.data.UserMemoryType
import com.denggl2.mason.data.evaluateMemoryWrite
import com.denggl2.mason.data.normalizeProjectId
import javax.inject.Inject
import javax.inject.Singleton

abstract class BaseMemoryWriteTool(
    private val store: UserMemoryStore,
    private val projectContextStore: ProjectContextStore,
    private val forceSensitive: Boolean,
) : Tool {
    override val parameters = mapOf(
        "label" to ParameterDef("string", "简短稳定的记忆名称，例如默认语言或姓名", required = true),
        "value" to ParameterDef("string", "需要长期保存的事实或偏好", required = true),
        "type" to ParameterDef(
            "string",
            "记忆类型",
            enum = UserMemoryType.entries.map(UserMemoryType::name),
        ),
        "explicit_request" to ParameterDef("boolean", "用户是否明确要求记住", required = false),
        "scope" to ParameterDef(
            "string",
            "记忆作用域。用户个人偏好使用 GLOBAL，当前项目的技术栈、约定和事实使用 PROJECT",
            enum = listOf(UserMemoryScope.GLOBAL.name, UserMemoryScope.PROJECT.name),
        ),
        "project_id" to ParameterDef(
            "string",
            "PROJECT 记忆所属的稳定项目名，例如 mason。当前会话已绑定项目时可省略",
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val requestedType = args["type"]?.let { raw ->
            UserMemoryType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
        val evaluation = evaluateMemoryWrite(
            label = args["label"].orEmpty(),
            value = args["value"].orEmpty(),
            requestedType = requestedType,
            requestedSensitive = forceSensitive,
            explicitRequest = args["explicit_request"].toBoolean(),
        )
        if (!evaluation.accepted) return ToolResult.error(evaluation.reason)
        if (evaluation.sensitive && !forceSensitive) {
            return ToolResult.error("检测到敏感信息，必须改用 memory_save_sensitive 并由用户确认")
        }
        val requestedScope = args["scope"]?.let { raw ->
            UserMemoryScope.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        } ?: UserMemoryScope.GLOBAL
        if (requestedScope == UserMemoryScope.CONVERSATION) {
            return ToolResult.error("对话级记忆不由模型长期写入")
        }
        val conversationId = args[INTERNAL_CONVERSATION_ID]
        val explicitProjectName = args["project_id"]?.trim().orEmpty()
        val explicitProjectId = normalizeProjectId(explicitProjectName)
        val boundProject = conversationId?.let(projectContextStore::get)
        val projectId = explicitProjectId.ifBlank { boundProject?.id.orEmpty() }
        if (requestedScope == UserMemoryScope.PROJECT && projectId.isBlank()) {
            return ToolResult.error("保存项目记忆前需要明确项目名称")
        }
        if (requestedScope == UserMemoryScope.PROJECT && conversationId != null && explicitProjectId.isNotBlank()) {
            projectContextStore.bind(
                conversationId,
                ProjectContext(id = explicitProjectId, displayName = explicitProjectName.take(40)),
            )
        }
        val outcome = store.saveEvaluatedMemory(
            evaluation = evaluation,
            scope = requestedScope,
            scopeId = projectId.takeIf { requestedScope == UserMemoryScope.PROJECT },
        )
        return ToolResult.success(
            mapOf(
                "status" to when (outcome.status) {
                    MemorySaveStatus.Created -> "created"
                    MemorySaveStatus.Updated -> "updated"
                    MemorySaveStatus.Unchanged -> "unchanged"
                },
                "memory_id" to outcome.item.id,
                "label" to outcome.item.label,
                "type" to outcome.item.type.name,
                "sensitive" to outcome.item.sensitive.toString(),
                "scope" to outcome.item.scope.name,
                "scope_id" to outcome.item.scopeId.orEmpty(),
            ),
        )
    }
}

@Singleton
class MemoryWriteTool @Inject constructor(
    store: UserMemoryStore,
    projectContextStore: ProjectContextStore,
) : BaseMemoryWriteTool(store, projectContextStore, forceSensitive = false) {
    override val name = NAME
    override val displayName = "保存长期记忆"
    override val description =
        "保存可跨任务复用的非敏感长期事实或偏好。不要保存当前任务状态、临时安排、猜测或一次性内容。"

    companion object { const val NAME = "memory_save" }
}

@Singleton
class SensitiveMemoryWriteTool @Inject constructor(
    store: UserMemoryStore,
    projectContextStore: ProjectContextStore,
) : BaseMemoryWriteTool(store, projectContextStore, forceSensitive = true) {
    override val name = NAME
    override val displayName = "保存敏感记忆"
    override val description = "保存姓名、地址、身份、账号或支付相关记忆。每次写入都必须由用户确认。"
    override val approvalDescription = "将敏感信息加密保存到本机记忆，可在设置中查看、修改或删除。"

    companion object { const val NAME = "memory_save_sensitive" }
}

const val INTERNAL_CONVERSATION_ID = "_mason_conversation_id"
