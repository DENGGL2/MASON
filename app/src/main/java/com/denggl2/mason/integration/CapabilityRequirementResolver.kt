package com.denggl2.mason.integration

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class CapabilityRequirementStatus {
    NotInstalled,
    NeedsAuthorization,
    WaitingForOfficialAccess,
    NeedsConnection,
    Unavailable,
}

@Serializable
data class CapabilityRequirement(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val protocol: CapabilityProtocol,
    val displayName: String,
    val capabilities: List<String>,
    val status: CapabilityRequirementStatus,
    val detail: String,
    val originalGoal: String,
    val taskRunId: String,
)

internal data class CapabilityIntent(
    val protocol: CapabilityProtocol,
    val providerId: String,
)

internal fun classifyCapabilityIntent(goal: String): CapabilityIntent? {
    val normalized = goal.lowercase()
    if (CapabilityRequirementResolver.appActionTerms.any(normalized::contains)) {
        CapabilityRequirementResolver.appAliases.entries.firstOrNull { (_, aliases) ->
            aliases.any(normalized::contains)
        }?.let { (providerId, _) -> return CapabilityIntent(CapabilityProtocol.A2A, providerId) }
    }
    return CapabilityRequirementResolver.mcpRequirements.firstOrNull { requirement ->
        requirement.aliases.any(normalized::contains) && requirement.actionTerms.any(normalized::contains)
    }?.let { requirement -> CapabilityIntent(CapabilityProtocol.MCP, requirement.id) }
}

@Singleton
class CapabilityRequirementResolver @Inject constructor(
    private val appAuthorization: AppCapabilityAuthorization,
    private val integrationStore: IntegrationStore,
    private val mcpToolManager: McpToolManager,
) {
    fun resolve(goal: String, taskRunId: String): CapabilityRequirement? {
        val intent = classifyCapabilityIntent(goal) ?: return null
        return when (intent.protocol) {
            CapabilityProtocol.A2A -> resolveAppCollaboration(intent.providerId, goal, taskRunId)
            CapabilityProtocol.MCP -> resolveMcpRequirement(intent.providerId, goal, taskRunId)
        }
    }

    fun isSatisfied(requirement: CapabilityRequirement): Boolean = when (requirement.protocol) {
        CapabilityProtocol.A2A -> CapabilityProviderCatalog.appCollaborations
            .firstOrNull { it.id == requirement.providerId }
            ?.let(appAuthorization::state)
            ?.status == CapabilityConnectionStatus.Connected
        CapabilityProtocol.MCP -> matchingMcpServers(requirement.providerId).any { server ->
            server.enabled && mcpToolManager.states.value[server.id]?.phase == IntegrationConnectionPhase.Online
        }
    }

    private fun resolveAppCollaboration(
        providerId: String,
        originalGoal: String,
        taskRunId: String,
    ): CapabilityRequirement? {
        val provider = CapabilityProviderCatalog.appCollaborations.firstOrNull { it.id == providerId } ?: return null
        val state = appAuthorization.state(provider)
        if (state.status == CapabilityConnectionStatus.Connected) return null
        return CapabilityRequirement(
            providerId = provider.id,
            protocol = CapabilityProtocol.A2A,
            displayName = provider.displayName,
            capabilities = requestedAppCapabilities(originalGoal, provider),
            status = state.status.toRequirementStatus(),
            detail = state.detail,
            originalGoal = originalGoal,
            taskRunId = taskRunId,
        )
    }

    private fun resolveMcpRequirement(
        requirementId: String,
        originalGoal: String,
        taskRunId: String,
    ): CapabilityRequirement? {
        val definition = mcpRequirements.firstOrNull { it.id == requirementId } ?: return null
        val matching = matchingMcpServers(definition.id)
        if (matching.any { server ->
                server.enabled && mcpToolManager.states.value[server.id]?.phase == IntegrationConnectionPhase.Online
            }
        ) return null
        val enabled = matching.firstOrNull(McpServerConfig::enabled)
        val detail = when {
            enabled != null -> "${enabled.name} 已配置，但连接尚未就绪"
            matching.isNotEmpty() -> "相关工具服务已配置但未启用"
            else -> "尚未连接可用的${definition.displayName}工具服务"
        }
        return CapabilityRequirement(
            providerId = definition.id,
            protocol = CapabilityProtocol.MCP,
            displayName = definition.displayName,
            capabilities = definition.capabilities,
            status = if (enabled == null) CapabilityRequirementStatus.NeedsConnection else CapabilityRequirementStatus.Unavailable,
            detail = detail,
            originalGoal = originalGoal,
            taskRunId = taskRunId,
        )
    }

    private fun matchingMcpServers(requirementId: String): List<McpServerConfig> {
        val aliases = mcpRequirements.firstOrNull { it.id == requirementId }?.aliases.orEmpty()
        return integrationStore.snapshot.value.mcpServers.filter { server ->
            val name = server.name.lowercase()
            aliases.any(name::contains) || requirementId == "mcp" && server.enabled
        }
    }

    private fun requestedAppCapabilities(goal: String, provider: CapabilityProvider): List<String> {
        val normalized = goal.lowercase()
        return when (provider.id) {
            "wechat" -> when {
                listOf("视频", "video").any(normalized::contains) -> listOf("视频通话")
                listOf("打电话", "通话", "call").any(normalized::contains) -> listOf("语音通话")
                else -> listOf("发送消息")
            }
            "alipay" -> when {
                listOf("账单", "bill").any(normalized::contains) -> listOf("账单查询")
                else -> listOf("付款准备")
            }
            "meituan" -> when {
                listOf("酒店", "hotel").any(normalized::contains) -> listOf("酒店")
                listOf("出行", "travel", "ride").any(normalized::contains) -> listOf("出行服务")
                else -> listOf("外卖")
            }
            else -> provider.capabilities
        }
    }

    internal data class McpRequirementDefinition(
        val id: String,
        val displayName: String,
        val aliases: List<String>,
        val actionTerms: List<String>,
        val capabilities: List<String>,
    )

    companion object {
        internal val appAliases = mapOf(
            "wechat" to listOf("微信", "wechat"),
            "alipay" to listOf("支付宝", "alipay"),
            "meituan" to listOf("美团", "meituan"),
        )
        internal val appActionTerms = listOf(
            "发消息", "发送消息", "告诉", "通知", "联系", "打电话", "通话", "视频", "付款", "支付",
            "下单", "点外卖", "订酒店", "send", "message", "call", "pay", "order",
        )
        internal val mcpRequirements = listOf(
            McpRequirementDefinition(
                id = "github",
                displayName = "GitHub",
                aliases = listOf("github", "代码仓库"),
                actionTerms = listOf("issue", "pull request", "pr", "仓库", "提交", "分支", "查询", "创建", "更新"),
                capabilities = listOf("读取仓库", "管理 Issue", "处理 Pull Request"),
            ),
            McpRequirementDefinition(
                id = "office",
                displayName = "办公系统",
                aliases = listOf("飞书", "钉钉", "office", "办公系统"),
                actionTerms = listOf("文档", "表格", "任务", "消息", "审批", "创建", "更新", "查询"),
                capabilities = listOf("文档", "任务", "消息与审批"),
            ),
            McpRequirementDefinition(
                id = "mcp",
                displayName = "MCP",
                aliases = listOf("mcp"),
                actionTerms = listOf("连接", "调用", "使用", "工具", "server", "服务"),
                capabilities = listOf("调用外部工具服务"),
            ),
        )
    }
}

private fun CapabilityConnectionStatus.toRequirementStatus(): CapabilityRequirementStatus = when (this) {
    CapabilityConnectionStatus.NotInstalled -> CapabilityRequirementStatus.NotInstalled
    CapabilityConnectionStatus.NeedsAuthorization -> CapabilityRequirementStatus.NeedsAuthorization
    CapabilityConnectionStatus.WaitingForOfficialAccess -> CapabilityRequirementStatus.WaitingForOfficialAccess
    CapabilityConnectionStatus.Unavailable -> CapabilityRequirementStatus.Unavailable
    CapabilityConnectionStatus.Connected -> CapabilityRequirementStatus.Unavailable
}

private const val CAPABILITY_MARKER_PREFIX = "<!-- mason-capability-request "
private const val CAPABILITY_MARKER_SUFFIX = " -->"
private val capabilityJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun CapabilityRequirement.toMarker(): String =
    CAPABILITY_MARKER_PREFIX + capabilityJson.encodeToString(this) + CAPABILITY_MARKER_SUFFIX

fun extractCapabilityRequirementMarker(content: String): CapabilityRequirement? = capabilityMarkerRegex
    .findAll(content)
    .lastOrNull()
    ?.groupValues
    ?.getOrNull(1)
    ?.let { encoded -> runCatching { capabilityJson.decodeFromString<CapabilityRequirement>(encoded) }.getOrNull() }

fun stripCapabilityRequirementMarkers(content: String): String = content.replace(capabilityMarkerRegex, "").trimEnd()

private val capabilityMarkerRegex = Regex(
    Regex.escape(CAPABILITY_MARKER_PREFIX) + "([\\s\\S]*?)" + Regex.escape(CAPABILITY_MARKER_SUFFIX),
)
