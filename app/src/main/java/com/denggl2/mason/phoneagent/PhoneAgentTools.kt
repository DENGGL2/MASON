package com.denggl2.mason.phoneagent

import com.denggl2.mason.tool.ParameterDef
import com.denggl2.mason.tool.Tool
import com.denggl2.mason.tool.ToolResult
import com.denggl2.mason.data.UiPreferencesDataStore
import com.denggl2.mason.localization.resolveAppStrings
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PhoneAgentActionRunner @Inject constructor(
    private val controller: PhoneAgentController,
    private val overlay: PhoneAgentOverlayController,
    private val logStore: PhoneAgentLogStore,
    private val uiPreferencesDataStore: UiPreferencesDataStore,
    @ApplicationContext private val context: Context,
) {
    suspend fun run(
        action: String,
        summary: String,
        snapshotVersion: Long? = null,
        focusPoint: PhoneAgentPoint? = null,
        block: suspend () -> ToolResult,
    ): ToolResult {
        val english = uiPreferencesDataStore.preferences.first()
            .let { context.resolveAppStrings(it.language).isEnglish }
        overlay.show(action, focusPoint, english, controller::pause)
        val result = runCatching { block() }
            .getOrElse { ToolResult.error("屏幕助手执行失败：${it.message}") }
        val resultVersion = result.data["snapshot_version"]?.toLongOrNull() ?: snapshotVersion
        logStore.append(
            action = action,
            summary = summary,
            result = result,
            packageName = controller.runtimeState.value.activePackage,
            snapshotVersion = resultVersion,
        )
        return result
    }
}

@Singleton
class PhoneObserveTool @Inject constructor(
    private val controller: PhoneAgentController,
    private val runner: PhoneAgentActionRunner,
) : Tool {
    override val name = PhoneAgentToolNames.OBSERVE
    override val displayName = "读取当前屏幕"
    override val description =
        "读取当前活动窗口的无障碍节点树并返回带版本号的节点 ID。执行点击、输入或滚动前应先调用；界面变化后必须重新读取。"
    override val approvalDescription = "读取当前屏幕中的文字、控件和位置"
    override val parameters = mapOf(
        "max_nodes" to ParameterDef(
            type = "integer",
            description = "最多返回的节点数，范围 20-300，默认 120",
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val maxNodes = args["max_nodes"]?.toIntOrNull() ?: 120
        return runner.run("读取屏幕", "读取当前窗口，最多 $maxNodes 个节点") {
            controller.observe(maxNodes)
        }
    }
}

@Singleton
class PhoneScreenshotTool @Inject constructor(
    private val controller: PhoneAgentController,
    private val runner: PhoneAgentActionRunner,
) : Tool {
    override val name = PhoneAgentToolNames.SCREENSHOT
    override val displayName = "截取当前屏幕"
    override val description = "通过无障碍服务截取当前屏幕并保存到应用缓存。截图不会自动上传给模型。"
    override val approvalDescription = "截取当前屏幕并保存到 Mason 本机缓存"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult =
        runner.run("屏幕截图", "截取当前屏幕并保存到本机缓存") { controller.screenshot() }
}

@Singleton
class PhoneClickNodeTool @Inject constructor(
    private val controller: PhoneAgentController,
    private val runner: PhoneAgentActionRunner,
) : Tool {
    override val name = PhoneAgentToolNames.CLICK_NODE
    override val displayName = "点击屏幕控件"
    override val description = "点击 phone_observe 最新快照中的节点。旧快照节点会被拒绝，优先使用该工具而不是坐标点击。"
    override val approvalDescription = "点击当前屏幕中的指定控件"
    override val parameters = mapOf(
        "node_id" to ParameterDef(
            type = "string",
            description = "phone_observe 返回的最新节点 ID",
            required = true,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val nodeId = args["node_id"]?.takeIf(String::isNotBlank)
            ?: return ToolResult.error("缺少 node_id 参数")
        return runner.run(
            action = "点击控件",
            summary = "点击节点 $nodeId",
            snapshotVersion = nodeId.snapshotVersion(),
            focusPoint = controller.nodeCenter(nodeId),
        ) { controller.clickNode(nodeId) }
    }
}

@Singleton
class PhoneTapTool @Inject constructor(
    private val controller: PhoneAgentController,
    private val runner: PhoneAgentActionRunner,
) : Tool {
    override val name = PhoneAgentToolNames.TAP
    override val displayName = "点击屏幕坐标"
    override val description = "点击屏幕绝对坐标。仅在目标控件没有可用节点 ID 时使用。"
    override val approvalDescription = "点击当前屏幕中的指定坐标"
    override val parameters = mapOf(
        "x" to ParameterDef("integer", "屏幕横坐标", required = true),
        "y" to ParameterDef("integer", "屏幕纵坐标", required = true),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val x = args.requiredInt("x") ?: return ToolResult.error("x 必须是整数")
        val y = args.requiredInt("y") ?: return ToolResult.error("y 必须是整数")
        return runner.run(
            action = "坐标点击",
            summary = "点击坐标 ($x, $y)",
            focusPoint = PhoneAgentPoint(x, y),
        ) { controller.tap(x, y) }
    }
}

@Singleton
class PhoneSwipeTool @Inject constructor(
    private val controller: PhoneAgentController,
    private val runner: PhoneAgentActionRunner,
) : Tool {
    override val name = PhoneAgentToolNames.SWIPE
    override val displayName = "滑动屏幕"
    override val description = "从一个屏幕坐标滑动到另一个坐标，可用于无法通过节点滚动的界面。"
    override val approvalDescription = "按指定轨迹滑动当前屏幕"
    override val parameters = mapOf(
        "start_x" to ParameterDef("integer", "起点横坐标", required = true),
        "start_y" to ParameterDef("integer", "起点纵坐标", required = true),
        "end_x" to ParameterDef("integer", "终点横坐标", required = true),
        "end_y" to ParameterDef("integer", "终点纵坐标", required = true),
        "duration_ms" to ParameterDef("integer", "持续时间，范围 100-2000，默认 400"),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val startX = args.requiredInt("start_x") ?: return ToolResult.error("start_x 必须是整数")
        val startY = args.requiredInt("start_y") ?: return ToolResult.error("start_y 必须是整数")
        val endX = args.requiredInt("end_x") ?: return ToolResult.error("end_x 必须是整数")
        val endY = args.requiredInt("end_y") ?: return ToolResult.error("end_y 必须是整数")
        val duration = args["duration_ms"]?.toLongOrNull() ?: 400L
        val summary = "从 ($startX, $startY) 滑动到 ($endX, $endY)，${duration.coerceIn(100, 2_000)}ms"
        return runner.run("滑动屏幕", summary) {
            controller.swipe(startX, startY, endX, endY, duration)
        }
    }
}

@Singleton
class PhoneScrollTool @Inject constructor(
    private val controller: PhoneAgentController,
    private val runner: PhoneAgentActionRunner,
) : Tool {
    override val name = PhoneAgentToolNames.SCROLL
    override val displayName = "滚动屏幕控件"
    override val description = "滚动 phone_observe 最新快照中的可滚动节点。界面变化后必须重新读取节点。"
    override val approvalDescription = "滚动当前屏幕中的指定控件"
    override val parameters = mapOf(
        "node_id" to ParameterDef("string", "最新快照中的可滚动节点 ID", required = true),
        "direction" to ParameterDef(
            type = "string",
            description = "滚动方向",
            required = true,
            enum = listOf("forward", "backward"),
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val nodeId = args["node_id"]?.takeIf(String::isNotBlank)
            ?: return ToolResult.error("缺少 node_id 参数")
        val direction = args["direction"] ?: return ToolResult.error("缺少 direction 参数")
        return runner.run(
            action = "滚动控件",
            summary = "节点 $nodeId，方向 $direction",
            snapshotVersion = nodeId.snapshotVersion(),
        ) { controller.scroll(nodeId, direction) }
    }
}

@Singleton
class PhoneSetTextTool @Inject constructor(
    private val controller: PhoneAgentController,
    private val runner: PhoneAgentActionRunner,
) : Tool {
    override val name = PhoneAgentToolNames.SET_TEXT
    override val displayName = "输入屏幕文字"
    override val description = "向 phone_observe 最新快照中的可编辑节点写入文字。日志只记录字符数，不记录输入内容。"
    override val approvalDescription = "向当前屏幕的指定输入框写入文字"
    override val parameters = mapOf(
        "node_id" to ParameterDef("string", "最新快照中的可编辑节点 ID", required = true),
        "text" to ParameterDef("string", "要输入的文字", required = true),
        "mode" to ParameterDef(
            type = "string",
            description = "replace 覆盖或 append 追加，默认 replace",
            enum = listOf("replace", "append"),
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val nodeId = args["node_id"]?.takeIf(String::isNotBlank)
            ?: return ToolResult.error("缺少 node_id 参数")
        if (!args.containsKey("text")) return ToolResult.error("缺少 text 参数")
        val text = args["text"].orEmpty()
        val mode = args["mode"] ?: "replace"
        if (mode !in setOf("replace", "append")) return ToolResult.error("mode 仅支持 replace 或 append")
        return runner.run(
            action = "输入文字",
            summary = "向节点 $nodeId ${if (mode == "append") "追加" else "写入"} ${text.length} 个字符",
            snapshotVersion = nodeId.snapshotVersion(),
        ) { controller.setText(nodeId, text, mode == "append") }
    }
}

@Singleton
class PhoneGlobalActionTool @Inject constructor(
    private val controller: PhoneAgentController,
    private val runner: PhoneAgentActionRunner,
) : Tool {
    override val name = PhoneAgentToolNames.GLOBAL_ACTION
    override val displayName = "执行系统导航"
    override val description = "执行返回、主页、最近任务、通知栏或快捷设置等 Android 系统导航动作。"
    override val approvalDescription = "执行指定的 Android 系统导航动作"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "系统动作",
            required = true,
            enum = listOf("back", "home", "recents", "notifications", "quick_settings"),
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult.error("缺少 action 参数")
        return runner.run("系统导航", "执行系统动作 $action") { controller.globalAction(action) }
    }
}

private fun Map<String, String>.requiredInt(key: String): Int? = get(key)?.toIntOrNull()

private fun String.snapshotVersion(): Long? =
    takeIf { startsWith('v') }
        ?.substringAfter('v')
        ?.substringBefore('_')
        ?.toLongOrNull()
