package com.denggl2.mason.integration

import com.denggl2.mason.tool.ParameterDef
import com.denggl2.mason.tool.Tool
import com.denggl2.mason.tool.ToolRegistry
import com.denggl2.mason.tool.ToolResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class A2aToolManager @Inject constructor(
    private val store: IntegrationStore,
    private val client: A2aClient,
    private val bridge: A2aTaskBridge,
    private val toolRegistry: ToolRegistry,
) {
    private val _states = MutableStateFlow<Map<String, IntegrationConnectionState>>(emptyMap())
    val states = _states.asStateFlow()

    suspend fun refreshAll() = coroutineScope {
        val agents = store.snapshot.value.a2aAgents
        _states.value = agents.associate { config ->
            config.id to IntegrationConnectionState(
                phase = if (config.enabled) IntegrationConnectionPhase.Connecting else IntegrationConnectionPhase.Disabled,
                displayName = config.name,
                detail = if (config.enabled) "正在发现" else "已停用",
            )
        }
        val tools = agents.filter(A2aAgentConfig::enabled).map { config ->
            async { discover(config) }
        }.awaitAll().mapNotNull { it }
        toolRegistry.replaceNamespace(A2A_TOOL_PREFIX, tools)
    }

    suspend fun refresh(agentId: String): IntegrationConnectionState {
        val config = store.snapshot.value.a2aAgents.firstOrNull { it.id == agentId }
            ?: return IntegrationConnectionState(IntegrationConnectionPhase.Error, detail = "A2A 配置不存在")
        val tool = config.takeIf(A2aAgentConfig::enabled)?.let { discover(it) }
        val ownPrefix = A2A_TOOL_PREFIX + config.id.integrationNamespace()
        val retained = toolRegistry.getAll().filter { it.name.startsWith(A2A_TOOL_PREFIX) && !it.name.startsWith(ownPrefix) }
        toolRegistry.replaceNamespace(A2A_TOOL_PREFIX, retained + listOfNotNull(tool))
        return _states.value[config.id] ?: IntegrationConnectionState(IntegrationConnectionPhase.Disabled, config.name)
    }

    private suspend fun discover(config: A2aAgentConfig): Tool? = runCatching {
        val descriptor = client.discover(config)
        _states.value = _states.value + (config.id to IntegrationConnectionState(
            phase = IntegrationConnectionPhase.Online,
            displayName = descriptor.name,
            detail = "${descriptor.protocolBinding} ${descriptor.protocolVersion} · ${descriptor.skills.size} 项能力",
            capabilityCount = descriptor.skills.size,
            checkedAt = System.currentTimeMillis(),
        ))
        A2aRemoteTool(config, descriptor, bridge)
    }.getOrElse { error ->
        _states.value = _states.value + (config.id to IntegrationConnectionState(
            phase = IntegrationConnectionPhase.Error,
            displayName = config.name,
            detail = error.message ?: error.javaClass.simpleName,
            checkedAt = System.currentTimeMillis(),
        ))
        null
    }

    companion object {
        const val A2A_TOOL_PREFIX = "a2a__"
        const val MASON_TASK_RUN_ID = "_mason_task_run_id"
    }
}

private class A2aRemoteTool(
    private val config: A2aAgentConfig,
    private val descriptor: A2aAgentDescriptor,
    private val bridge: A2aTaskBridge,
) : Tool {
    override val name: String = A2aToolManager.A2A_TOOL_PREFIX + config.id.integrationNamespace() + "__delegate"
    override val displayName: String = descriptor.name
    override val approvalDescription: String = buildString {
        append("把当前任务交给 ${descriptor.name} 处理")
        if (descriptor.skills.isNotEmpty()) append("，可使用：${descriptor.skills.joinToString("、")}")
    }
    override val description: String = buildString {
        append("将任务委派给 ${descriptor.name}: ${descriptor.description}")
        if (descriptor.skills.isNotEmpty()) append("。能力：${descriptor.skills.joinToString("、")}")
    }
    override val parameters: Map<String, ParameterDef> = mapOf(
        "goal" to ParameterDef("string", "要交给外部 Agent 完成的具体目标", required = true),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val goal = args["goal"].orEmpty().trim()
        if (goal.isBlank()) return ToolResult.error("A2A 任务目标不能为空")
        return bridge.delegate(config, descriptor, goal, args[A2aToolManager.MASON_TASK_RUN_ID])
    }
}
