package com.denggl2.masonremote.ui

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.util.Locale
import com.denggl2.masonremote.ui.settings.RemoteLanguagePreference
import com.denggl2.masonremote.ui.settings.RemoteFontSizePreference
import com.denggl2.masonremote.ui.settings.RemoteInterfaceStyle
import com.denggl2.masonremote.ui.settings.RemoteMessageSendMode
import com.denggl2.masonremote.ui.settings.RemoteThemeMode
import com.denggl2.masonremote.ui.settings.TaskNotificationMode

/** The two UI languages supported by the phone client. */
enum class RemoteResolvedLanguage {
    CHINESE,
    ENGLISH,
}

@Immutable
class RemoteStrings internal constructor(
    val language: RemoteResolvedLanguage,
) {
    val isEnglish: Boolean
        get() = language == RemoteResolvedLanguage.ENGLISH

    fun text(chinese: String, english: String): String =
        if (isEnglish) english else chinese

    /** Translate a known app-owned string. Unknown values are intentionally kept intact. */
    fun t(chinese: String): String = if (isEnglish) translateKnownText(chinese) else chinese

    /** Text values used by the Compose display layer, including common dynamic labels. */
    fun displayText(value: String): String {
        if (!isEnglish || value.isEmpty()) return value
        translateExactText(value).takeIf { it != value }?.let { return it }
        translateWebRtcPairingFailure(value)?.let { return it }
        translateWebRtcSignalingFailure(value)?.let { return it }
        translateIntegrationText(value)?.let { return it }
        return when {
            value.startsWith("已配对（") && value.endsWith("）") ->
                "Paired (${value.removePrefix("已配对（").removeSuffix("）")})"
            value.startsWith("已配对 ") && value.contains("（") && value.endsWith("）") ->
                "Paired ${value.removePrefix("已配对 ").substringBefore("（")} (${value.substringAfter("（").removeSuffix("）")})"
            value.startsWith("已连接 · ") -> "Connected · ${value.removePrefix("已连接 · ")}"
            value.startsWith("离线 · ") -> "Offline · ${value.removePrefix("离线 · ")}"
            value.startsWith("第 ") && value.endsWith(" 次尝试") ->
                "Attempt ${value.removePrefix("第 ").removeSuffix(" 次尝试")}"
            value.startsWith("来源 ") -> "Source ${value.removePrefix("来源 ")}"
            value.startsWith("已选 ") -> "Selected ${value.removePrefix("已选 ")}"
            value.startsWith("已下载 ") -> "Downloaded ${value.removePrefix("已下载 ")}"
            value.startsWith("图片生成 ") -> "Image generation ${value.removePrefix("图片生成 ")}"
            value.startsWith("工具结果 · ") -> "Tool result · ${value.removePrefix("工具结果 · ")}"
            value.startsWith("模式 · ") -> "Mode · ${value.removePrefix("模式 · ")}"
            value.startsWith("每 ") && value.endsWith(" 分钟") ->
                "Every ${value.removePrefix("每 ").removeSuffix(" 分钟")} minutes"
            value.startsWith("每天 ") -> "Daily at ${value.removePrefix("每天 ")}"
            value.startsWith("连接 WiFi：") -> "Connect to Wi-Fi: ${value.removePrefix("连接 WiFi：")}"
            value.startsWith("连接蓝牙：") -> "Connect to Bluetooth: ${value.removePrefix("连接蓝牙：")}"
            value.startsWith("收到通知：") -> "Notification received: ${value.removePrefix("收到通知：")}"
            value.startsWith("连接 ") && value.contains("：") ->
                "Connect ${value.substringAfter("连接 ").replaceFirst("：", ": ")}"
            value.startsWith("等待连接 ") -> "Waiting for ${value.removePrefix("等待连接 ")} connection"
            value.startsWith("级别：") -> "Level: ${value.removePrefix("级别：")}"
            value.startsWith("影响范围：") -> "Scope: ${value.removePrefix("影响范围：")}"
            value.startsWith("协作方式：") -> "Collaboration: ${value.removePrefix("协作方式：")}"
            value.startsWith("用时 ") -> "Duration ${value.removePrefix("用时 ")}"
            value.startsWith("复制") && value.length > 2 -> "Copy ${value.removePrefix("复制")}"
            value.startsWith("已复制") && value.length > 3 -> "Copied ${value.removePrefix("已复制")}"
            value.startsWith("分享 ") -> "Share ${value.removePrefix("分享 ")}"
            value.startsWith("预览 ") -> "Preview ${value.removePrefix("预览 ")}"
            value.startsWith("石头屏幕助手，") ->
                "Stone screen assistant, ${translateKnownText(value.removePrefix("石头屏幕助手，"))}"
            value.startsWith("打开失败：") -> "Open failed: ${value.removePrefix("打开失败：")}"
            value.startsWith("打开分享面板失败：") -> "Unable to open the share sheet: ${value.removePrefix("打开分享面板失败：")}"
            value.startsWith("图片无法分享：") -> "Unable to share the image: ${value.removePrefix("图片无法分享：")}"
            value.startsWith("读取预览失败：") -> "Preview read failed: ${value.removePrefix("读取预览失败：")}"
            value.startsWith("当前模型需要 API Key") -> "The current model needs an API key; add it in Settings"
            value.startsWith("已暂停：") -> "Paused: ${value.removePrefix("已暂停：")}"
            value.startsWith("正在下载：") -> "Downloading: ${value.removePrefix("正在下载：")}"
            value.startsWith("已安装 ") && value.endsWith(" 个") ->
                "${value.removePrefix("已安装 ").removeSuffix(" 个")} installed"
            value.startsWith("已安装 ") -> "Installed ${value.removePrefix("已安装 ")}"
            value.startsWith("已创建 ") -> "Created ${value.removePrefix("已创建 ")}"
            value.startsWith("已更新 ") -> "Updated ${value.removePrefix("已更新 ")}"
            value.startsWith("已启用 ") -> "Enabled ${value.removePrefix("已启用 ")}"
            value.startsWith("已停用 ") -> "Disabled ${value.removePrefix("已停用 ")}"
            value.startsWith("自动化草稿 · ") -> "Automation draft · ${value.removePrefix("自动化草稿 · ")}"
            value.startsWith("发送失败：") -> "Send failed: ${displayText(value.removePrefix("发送失败："))}"
            value.startsWith("图片读取失败：") -> "Image read failed: ${displayText(value.removePrefix("图片读取失败："))}"
            value.startsWith("应用：") -> "App: ${value.removePrefix("应用：")}"
            value.startsWith("已下载到 ") -> "Downloaded to ${value.removePrefix("已下载到 ")}"
            value.startsWith("已保存到 ") -> "Saved to ${value.removePrefix("已保存到 ")}"
            value.startsWith("保存失败：") -> "Save failed: ${displayText(value.removePrefix("保存失败："))}"
            value.startsWith("分享失败：") -> "Share failed: ${displayText(value.removePrefix("分享失败："))}"
            value.startsWith("错误码") -> "Error ${value.removePrefix("错误码")}"
            value.startsWith("已断开连接，请重试\n") ->
                "Connection lost. Please retry\n${value.substringAfter("\n")}"
            value.startsWith("电脑文件 · ") -> "Computer file · ${value.removePrefix("电脑文件 · ")}"
            value.startsWith("进行中 · ") -> "In progress · ${value.removePrefix("进行中 · ")}"
            value.startsWith("耗时") -> "Duration ${value.removePrefix("耗时")}"
            value.startsWith("已执行") && value.endsWith("条") -> {
                val count = value.removePrefix("已执行").removeSuffix("条").trim()
                "Executed $count ${if (count == "1") "item" else "items"}"
            }
            value.startsWith("当前连接方式为") && value.endsWith("，是否断开？") ->
                "Connection method: ${displayText(value.removePrefix("当前连接方式为").removeSuffix("，是否断开？"))}. Disconnect?"
            value.startsWith("请扫描 ") && value.endsWith("二维码") ->
                "Scan the ${displayText(value.removePrefix("请扫描 ").removeSuffix("二维码"))} QR code"
            value.contains("\n预计清理 ") ->
                "Only image preview cache will be cleared\nEstimated: ${value.substringAfter("\n预计清理 ")}"
            value.contains("服务器\n") || value.contains("设备指纹\n") || value.contains("二维码有效期\n") ->
                value.replace("服务器\n", "Server\n")
                    .replace("设备指纹\n", "Device fingerprint\n")
                    .replace("二维码有效期\n", "QR code validity\n")
                    .replace("\n剩余 ", "\n")
                    .replace("分", "m ")
                    .replace("秒", "s")
            value.startsWith("有效至 ") -> value
                .replace("（剩余 ", " ( ")
                .replace("分", "m ")
                .replace("秒）", "s remaining)")
            value.startsWith("二维码已过期") -> "The QR code has expired. Ask the computer app to generate a new one"
            value.startsWith("诊断日志导出失败：") ->
                "Diagnostic log export failed: ${displayText(value.removePrefix("诊断日志导出失败："))}"
            value.startsWith("图片预览缓存") -> value.replace("图片预览缓存已清理", "Image preview cache cleared")
            value.startsWith("每次最多添加 ") && value.endsWith(" 个附件") ->
                "You can add up to ${value.removePrefix("每次最多添加 ").removeSuffix(" 个附件")} attachments at a time"
            value.startsWith("无法创建 ") -> "Unable to create ${value.removePrefix("无法创建 ")}"
            value.startsWith("仅显示最近 ") && value.endsWith(" 条文字消息") ->
                "Showing only the latest ${value.removePrefix("仅显示最近 ").removeSuffix(" 条文字消息")} text messages"
            value.startsWith("运行 ") && value.endsWith("？") ->
                "Run ${value.removePrefix("运行 ").removeSuffix("？")}?"
            value.endsWith(" · 运行日志") -> "${value.removeSuffix(" · 运行日志")} · Run log"
            value.startsWith("填写 ") && value.endsWith(" 的 Key") ->
                "Enter the key for ${value.removePrefix("填写 ").removeSuffix(" 的 Key")}"
            value.startsWith("已配对 ") -> "Paired ${value.removePrefix("已配对 ")}"
            value.startsWith("已删除 ") && value.endsWith(" 个产出") ->
                "Deleted ${value.removePrefix("已删除 ").removeSuffix(" 个产出")} artifacts"
            value.startsWith("已导入 ") && value.endsWith(" 个对话") ->
                "Imported ${value.removePrefix("已导入 ").removeSuffix(" 个对话")} conversations"
            value.endsWith(" 个已安装") -> "${value.removeSuffix(" 个已安装")} installed"
            value.endsWith(" 个已配置模型") ->
                "${value.removeSuffix(" 个已配置模型")} configured models"
            value.endsWith(" 个可选模型") ->
                "${value.removeSuffix(" 个可选模型")} available models"
            value.startsWith("向 ") && value.endsWith(" 发送消息") ->
                "Send a message to ${value.removePrefix("向 ").removeSuffix(" 发送消息")}"
            value.startsWith("正在测试模型 ") ->
                "Testing models ${value.removePrefix("正在测试模型 ")}"
            value.startsWith("已撤销 ") && value.endsWith(" 的永久授权") ->
                "Revoked permanent access for ${value.removePrefix("已撤销 ").removeSuffix(" 的永久授权")}"
            value.startsWith("已取消 ") && value.endsWith(" 工具调用。这次操作没有继续执行，手机状态不会被更改。") ->
                "Canceled the ${value.removePrefix("已取消 ").removeSuffix(" 工具调用。这次操作没有继续执行，手机状态不会被更改。")} tool call. The action did not continue and the phone state was not changed."
            value.startsWith("将从当前服务商移除 Model ID：") && value.endsWith("。") ->
                "Remove Model ID ${value.removePrefix("将从当前服务商移除 Model ID：").removeSuffix("。")} from the current provider."
            value.startsWith("将从当前配置草稿中移除 ") && value.endsWith("。") ->
                "Remove ${value.removePrefix("将从当前配置草稿中移除 ").removeSuffix("。")} from this configuration draft."
            value.startsWith("将删除 ") && value.endsWith(" 个文件，删除后不能恢复。") ->
                "This will delete ${value.removePrefix("将删除 ").removeSuffix(" 个文件，删除后不能恢复。")} files and cannot be undone."
            value.startsWith("将删除 ") && value.contains(" 个对话及其中消息，删除后不能恢复。") ->
                "This will delete ${value.removePrefix("将删除 ").substringBefore(" 个对话及其中消息")} conversations and their messages and cannot be undone."
            value.startsWith("将移除 ") && value.endsWith(" 的连接配置，不会影响远端服务。") ->
                "This will remove the connection for ${value.removePrefix("将移除 ").removeSuffix(" 的连接配置，不会影响远端服务。")} without affecting the remote service."
            value == "关闭推理" -> "Off"
            value == "极简" -> "Minimal"
            value == "默认推理" -> "Default reasoning"
            value == "低" -> "Low"
            value == "中" -> "Medium"
            value == "高" -> "High"
            value == "极高" -> "Very high"
            value == "最高" -> "Max"
            value == "超高" -> "Ultra"
            value == "未读取" -> "Unavailable"
            value == "请求批准" -> "Ask for approval"
            value == "帮我批准" -> "Approve for me"
            value == "完全访问权限" -> "Full access"
            value == "进行中" -> "In progress"
            value == "已执行" -> "Executed"
            value == "条" -> " items"
            value.matches(Regex("\\d+ 条本地崩溃诊断记录")) ->
                "${value.substringBefore(" 条")} local crash diagnostic ${if (value.startsWith("1 ")) "record" else "records"}"
            value.matches(Regex("\\d+ 个对话，\\d+ 条消息；不会在缓存清理中删除")) -> {
                val conversationCount = value.substringBefore(" 个对话")
                val messageCount = value.substringAfter(" 个对话，").substringBefore(" 条消息")
                val conversationWord = if (conversationCount == "1") "conversation" else "conversations"
                val messageWord = if (messageCount == "1") "message" else "messages"
                "$conversationCount $conversationWord, $messageCount $messageWord; not removed by this cleanup"
            }
            else -> translateHostPhrases(value)
        }
    }

    private fun translateWebRtcPairingFailure(value: String): String? {
        val (sourcePrefix, targetPrefix) = when {
            value.startsWith("RTC 配对超时（阶段：") -> "RTC 配对超时（阶段：" to "RTC pairing timed out (stage: "
            value.startsWith("RTC 配对失败（阶段：") -> "RTC 配对失败（阶段：" to "RTC pairing failed (stage: "
            else -> return null
        }
        val remainder = value.removePrefix(sourcePrefix)
        val metadataEnd = remainder.indexOf('）')
        if (metadataEnd < 0) return null
        val metadata = remainder.substring(0, metadataEnd)
        val iceMarker = "，ICE 状态："
        val turnMarker = "，TURN："
        val iceIndex = metadata.indexOf(iceMarker)
        val turnIndex = metadata.indexOf(turnMarker)
        if (iceIndex < 0 || turnIndex <= iceIndex) return null
        val stage = metadata.substring(0, iceIndex)
        val iceState = metadata.substring(iceIndex + iceMarker.length, turnIndex)
        val turnState = metadata.substring(turnIndex + turnMarker.length)
        val detail = remainder.substring(metadataEnd + 1).removePrefix("：")
        return buildString {
            append(targetPrefix)
            append(remoteEnglishText(stage))
            append(", ICE state: ")
            append(iceState)
            append(", TURN: ")
            append(
                when (turnState) {
                    "未配置" -> "not configured"
                    "已配置" -> "configured"
                    else -> turnState
                },
            )
            append(')')
            if (detail.isNotBlank()) {
                append(": ")
                append(displayText(detail))
            }
        }
    }

    private fun translateWebRtcSignalingFailure(value: String): String? {
        val (sourcePrefix, targetPrefix) = WEBRTC_SIGNALING_FAILURE_PREFIXES
            .firstOrNull { (source, _) -> value.startsWith(source) }
            ?: return null
        val suffix = value.removePrefix(sourcePrefix)
        if (!suffix.startsWith('（')) return targetPrefix + suffix
        val statusEnd = suffix.indexOf('）')
        if (statusEnd < 0) return targetPrefix + suffix
        return buildString {
            append(targetPrefix)
            append(" (")
            append(suffix.substring(1, statusEnd))
            append(')')
            append(suffix.substring(statusEnd + 1))
        }
    }

    /** Translate app-authored markdown snippets while preserving user content. */
    fun content(value: String): String {
        if (!isEnglish || value.isEmpty()) return value
        val translated = message(value)
        if (translated != value) return translated.orEmpty()
        val replacements = listOf(
            "任务结果" to "Task result",
            "这是普通文本，支持" to "This is regular text with support for",
            "这是普通文本，支持 **加粗**、`灰底代码` 和 [可点击链接](https://github.com/openai/codex)。" to
                "This is regular text with support for **bold text**, `inline code`, and [a clickable link](https://github.com/openai/codex).",
            "加粗" to "bold text",
            "灰底代码" to "inline code",
            "这一行与上一行之间保留换行。" to "The line break between this line and the previous one is preserved.",
            "这是引用内容。" to "This is quoted content.",
            "无序列表项目" to "Unordered list item",
            "第二个项目" to "Second item",
            "有序列表项目" to "Ordered list item",
            "已完成任务" to "Completed task",
            "待处理任务" to "Pending task",
            "状态" to "Status",
            "数量" to "Count",
            "完成" to "Completed",
            "进行中" to "In progress",
            "新增行" to "Added line",
            "删除行" to "Removed line",
            "保留行" to "Unchanged line",
            "展示手机端富文本效果" to "Show rich text on the phone",
            "把 Codex 的执行状态整理给我看" to "Show me the Codex execution status",
            "过程会按事件顺序显示，进行中的任务直接跟随对应行更新。" to "Events appear in order, and running tasks update beside their row.",
            "第一次思考更新：分析任务目标。" to "First thinking update: analyze the task goal.",
            "第二次思考更新：确认执行步骤。" to "Second thinking update: confirm the execution steps.",
            "开始检查电脑端工作区。" to "Start checking the computer workspace.",
            "第一条命令输出。" to "Output from the first command.",
            "第二条命令输出：同类命令更新覆盖上一条。" to "Output from the second command: the latest update replaces the previous one.",
            "搜索结果已返回。" to "Search results returned.",
            "调用远程工具并等待结果。" to "Call a remote tool and wait for the result.",
            "已写入目标文件。" to "The target file was written.",
            "计划已更新。" to "The plan was updated.",
            "已生成图片预览。" to "Image preview generated.",
            "其他操作已完成。" to "The other operation is complete.",
            "整理 CloudX 远程控制项目" to "Organize the CloudX remote-control project",
            "我正在检查工作区结构，并准备整理远程控制相关模块。" to "I am checking the workspace structure and preparing the remote-control modules.",
            "正在扫描工作区，等待电脑端返回任务进度。" to "Scanning the workspace and waiting for task progress from the computer.",
            "检查远程电脑端工作区" to "Check the remote computer workspace",
            "任务已完成。执行记录默认收起，点击执行行可以展开查看具体命令。" to "Task complete. Execution details are collapsed by default; tap a run row to view the command.",
            "命令已完成。" to "The command is complete.",
            "文件修改已完成。" to "The file change is complete.",
            "修复扫码连接超时" to "Fix QR connection timeout",
            "任务失败：未收到电脑端响应。请确认电脑端项目已经启动。" to "Task failed: no response from the computer. Make sure the computer project is running.",
            "未在等待时间内收到电脑端响应。" to "No response from the computer arrived in time.",
            "测试远程任务恢复" to "Test remote task recovery",
            "任务已中断，可以从最近消息继续。" to "Task interrupted. You can continue from the latest message.",
            "任务在电脑端停止，当前页面保留最近消息。" to "The task stopped on the computer; this page keeps the latest messages.",
            "已完成：这是电脑端 Codex 返回的对话内容。你可以展开下面的过程行查看任务详情。" to "Complete: this is the conversation returned by Codex on the computer. Expand the process row below for details.",
            "已完成本次任务。这里会显示电脑端返回的过程和任务结果。" to "This task is complete. The process and result returned by the computer appear here.",
            "电脑端正在等待你的允许" to "The computer is waiting for your approval",
            "等待电脑端确认请求。" to "Waiting for an approval request from the computer.",
            "运行需要确认的远程命令" to "Run a remote command that needs approval",
            "电脑端正在等待你的允许，确认后会继续执行。" to "The computer is waiting for your approval and will continue after confirmation.",
        )
        return replacements
            .sortedByDescending { (source, _) -> source.length }
            .fold(value) { result, (source, target) -> result.replace(source, target) }
    }

    fun multiline(chinese: String, english: String): String = text(chinese, english)

    /** Translate app-generated messages while leaving remote-authored content untouched. */
    fun message(message: String?): String? {
        if (!isEnglish || message.isNullOrBlank()) return message
        return translateKnownText(message)
    }
}

/**
 * Host-owned strings live here so the embedded Remote module can provide one
 * language switch for the whole MASON surface.  Unknown values are deliberately
 * left untouched: model names, paths, URLs, user messages, and remote replies
 * are data, not UI copy.
 */
private val HOST_ENGLISH_TEXT = mapOf(
    "英文" to "English",
    "设备本地运行" to "Runs locally",
    "+ 添加" to "+ Add",
    "添加模型 API 配置" to "Add model API configuration",
    "添加模型" to "Add model",
    "例如 https://api.example.com/v1" to "e.g. https://api.example.com/v1",
    "填写 API Key" to "Enter API key",
    "中文" to "Chinese",
    "跟随系统" to "Follow system",
    "设置" to "Settings",
    "新对话" to "New conversation",
    "设备扫码配对" to "Pair a device",
    "工作台" to "Workbench",
    "外观" to "Appearance",
    "当前模型" to "Current model",
    "聊天" to "Chat",
    "识图" to "Vision",
    "图片生成" to "Image generation",
    "本地模型" to "Local model",
    "模型管理" to "Model management",
    "模型接口" to "Model API",
    "屏幕助手" to "Screen assistant",
    "开启屏幕助手" to "Enable screen assistant",
    "最近使用日志" to "Recent activity log",
    "通过读取屏幕并操作按钮、滑动、打开、关闭等交互完成提出的任务，敏感操作会提前申请" to
        "Complete tasks by reading the screen and interacting with buttons, scrolling, opening, and closing; sensitive actions require approval first",
    "需要授权系统的无障碍和悬浮窗功能权限，每次行为都会保存到最近使用记录中" to
        "Accessibility and display-over-other-apps permissions are required; each action is saved to the recent activity log",
    "配置功能" to "Configure features",
    "远程" to "Remote",
    "进行中发送消息" to "Messages during a task",
    "插队" to "Send now",
    "排队" to "Queue",
    "通知" to "Notifications",
    "任务通知" to "Task notifications",
    "常规通知" to "Regular notifications",
    "岛通知" to "Island notifications",
    "启用常规通知" to "Enable regular notifications",
    "启用岛通知" to "Enable island notifications",
    "不启用" to "Disabled",
    "深色模式" to "Dark mode",
    "浅色" to "Light",
    "深色" to "Dark",
    "字体大小" to "Font size",
    "小" to "Small",
    "中" to "Medium",
    "大" to "Large",
    "超大" to "Extra large",
    "语言" to "Language",
    "风格" to "Style",
    "原生" to "Native",
    "玻璃" to "Glass",
    "主题色" to "Theme color",
    "黑色" to "Black",
    "折射效果" to "Refraction",
    "透明度" to "Transparency",
    "输入透明度" to "Enter transparency",
    "霜冻" to "Frost",
    "输入霜冻强度" to "Enter frost strength",
    "模型接口" to "Model API",
    "服务商" to "Provider",
    "中转站" to "Relay",
    "远端模型" to "Remote model",
    "其他设置" to "Other settings",
    "权限" to "Permissions",
    "权限管理" to "Permission management",
    "关于" to "About",
    "官方通道" to "Official channels",
    "管理" to "Manage",
    "返回" to "Back",
    "确定" to "Confirm",
    "确认" to "Confirm",
    "取消" to "Cancel",
    "删除" to "Delete",
    "保存" to "Save",
    "打开" to "Open",
    "编辑" to "Edit",
    "分享" to "Share",
    "下载" to "Download",
    "导出" to "Export",
    "导入" to "Import",
    "添加" to "Add",
    "移除" to "Remove",
    "重试" to "Retry",
    "重新测试" to "Test again",
    "继续" to "Continue",
    "停止" to "Stop",
    "暂停" to "Pause",
    "恢复" to "Resume",
    "关闭" to "Off",
    "知道了" to "Got it",
    "全选" to "Select all",
    "查看" to "View",
    "前往" to "Go",
    "前往设置" to "Open settings",
    "设置>" to "Settings >",
    "去配置" to "Configure",
    "去授权" to "Authorize",
    "待授权" to "Authorization required",
    "待确认" to "Awaiting confirmation",
    "待连接" to "Waiting for connection",
    "待处理" to "Pending",
    "待测试" to "Not tested",
    "待验证" to "Not verified",
    "已授权" to "Authorized",
    "已配置" to "Configured",
    "已安装" to "Installed",
    "已保存" to "Saved",
    "已完成" to "Completed",
    "已停止" to "Stopped",
    "已取消" to "Canceled",
    "已暂停" to "Paused",
    "已启用" to "Enabled",
    "已停用" to "Disabled",
    "成功" to "Success",
    "失败" to "Failed",
    "异常" to "Error",
    "处理中" to "Processing",
    "正在处理" to "Processing",
    "正在发送" to "Sending",
    "正在读取" to "Reading",
    "读取中" to "Reading",
    "加载中" to "Loading",
    "正在下载" to "Downloading",
    "下载中" to "Downloading",
    "正在上传附件" to "Uploading attachment",
    "正在思考" to "Thinking",
    "思考" to "Thinking",
    "思考已停止" to "Thinking stopped",
    "思考遇到问题" to "Thinking failed",
    "正在组织回答" to "Composing answer",
    "正在组织回复" to "Composing reply",
    "回复" to "Reply",
    "引导" to "Guidance",
    "最终总结" to "Final summary",
    "最后总结" to "Final summary",
    "任务" to "Task",
    "任务内容" to "Task content",
    "任务进展" to "Task progress",
    "执行" to "Run",
    "执行中" to "Running",
    "执行步骤" to "Execution steps",
    "执行命令" to "Run command",
    "执行失败" to "Execution failed",
    "执行条件" to "Execution conditions",
    "执行方式" to "Execution mode",
    "执行内容" to "Execution content",
    "执行说明" to "Execution details",
    "工具" to "Tool",
    "工具调用" to "Tool call",
    "工具结果" to "Tool result",
    "工具没有返回可展示内容" to "The tool returned no displayable content",
    "搜索" to "Search",
    "搜索网页" to "Search the web",
    "搜索对话" to "Search conversations",
    "搜索对话和内容" to "Search conversations and content",
    "计划" to "Plan",
    "更新计划" to "Update plan",
    "修改" to "Edit",
    "修改文件" to "Edit file",
    "图片" to "Image",
    "图像" to "Image",
    "文件" to "File",
    "附件" to "Attachment",
    "表格" to "Table",
    "代码" to "Code",
    "产出" to "Artifact",
    "预览产出" to "Preview artifact",
    "打开产出" to "Open artifact",
    "编辑产出" to "Edit artifact",
    "分享产出" to "Share artifact",
    "图片预览" to "Image preview",
    "文件预览" to "File preview",
    "关闭预览" to "Close preview",
    "关闭文件预览" to "Close file preview",
    "全屏查看" to "View full screen",
    "复制" to "Copy",
    "已复制" to "Copied",
    "复制消息" to "Copy message",
    "复制回答" to "Copy answer",
    "复制代码" to "Copy code",
    "复制表格" to "Copy table",
    "重发" to "Resend",
    "重新生成" to "Regenerate",
    "分享回答" to "Share answer",
    "分享图片" to "Share image",
    "保存图片到本地" to "Save image locally",
    "正在保存图片" to "Saving image",
    "添加图片" to "Add image",
    "添加文件" to "Add file",
    "使用 Skill" to "Use Skill",
    "移除 Skill" to "Remove Skill",
    "移除附件" to "Remove attachment",
    "暂无内容" to "No content",
    "暂无返回内容" to "No response yet",
    "暂无可用模型" to "No models available",
    "暂无模型" to "No models",
    "暂无已安装技能" to "No installed skills",
    "暂无已配置模型" to "No configured models",
    "暂无本地记忆" to "No local memories",
    "暂无对话生成文件" to "No files generated by conversations",
    "暂无屏幕助手使用记录" to "No screen assistant history",
    "还没有消息" to "No messages yet",
    "没有匹配的对话" to "No matching conversations",
    "没有匹配结果" to "No matching results",
    "未配置" to "Not configured",
    "当前未配置" to "Not configured",
    "当前不可用" to "Currently unavailable",
    "可用" to "Available",
    "可运行" to "Runnable",
    "无" to "None",
    "不限" to "Any",
    "本地" to "Local",
    "远端" to "Remote",
    "在线" to "Online",
    "离线" to "Offline",
    "连接" to "Connect",
    "连接中" to "Connecting",
    "已连接" to "Connected",
    "断开连接" to "Disconnect",
    "断开中" to "Disconnecting",
    "断开失败，请检查连接后重试" to "Disconnect failed. Check the connection and try again",
    "查看连接" to "View connection",
    "远端电脑配置" to "Remote computer",
    "电脑" to "Computer",
    "设备配对" to "Device pairing",
    "取消配对" to "Unpair",
    "新建远端对话" to "New remote conversation",
    "发起对话" to "Start conversation",
    "发送" to "Send",
    "发送消息" to "Send message",
    "停止生成" to "Stop generation",
    "回到最新消息" to "Jump to latest message",
    "展开" to "Expand",
    "收起" to "Collapse",
    "展开选项" to "Expand options",
    "收起选项" to "Collapse options",
    "展开详情" to "Expand details",
    "收起详情" to "Collapse details",
    "展开模型列表" to "Expand model list",
    "收起模型列表" to "Collapse model list",
    "当前选项" to "Current option",
    "当前模式" to "Current mode",
    "当前风格" to "Current style",
    "当前字体大小" to "Current font size",
    "当前语言" to "Current language",
    "当前主题色" to "Current theme color",
    "当前区域" to "Current region",
    "当前状态" to "Current status",
    "模型" to "Model",
    "模型名称" to "Model name",
    "选择模型" to "Select model",
    "选择项目" to "Select project",
    "选择权限" to "Select permissions",
    "选择聊天模型" to "Select chat model",
    "选择识图模型" to "Select vision model",
    "选择生图模型" to "Select image model",
    "选择本地备用" to "Select local fallback",
    "设为聊天模型" to "Set as chat model",
    "设为识图模型" to "Set as vision model",
    "设为生图模型" to "Set as image model",
    "设为本地备用" to "Set as local fallback",
    "主对话" to "Main chat",
    "生图" to "Image generation",
    "推理" to "Reasoning",
    "推理层级" to "Reasoning level",
    "默认推理" to "Default reasoning",
    "关闭推理" to "Off",
    "极低" to "Minimal",
    "极简" to "Minimal",
    "低" to "Low",
    "高" to "High",
    "极高" to "Very high",
    "最高" to "Max",
    "超高" to "Ultra",
    "访问权限" to "Permissions",
    "请求批准" to "Ask for approval",
    "帮我批准" to "Approve for me",
    "完全访问权限" to "Full access",
    "未读取" to "Unavailable",
    "总是允许" to "Always allow",
    "允许一次" to "Allow once",
    "拒绝" to "Decline",
    "允许" to "Allow",
    "风险确认" to "Risk confirmation",
    "协作方式" to "Collaboration",
    "级别" to "Level",
    "影响范围" to "Scope",
    "参与模型" to "Models involved",
    "本机" to "This device",
    "手机" to "Phone",
    "系统" to "System",
    "硬件" to "Hardware",
    "通讯" to "Communication",
    "网络" to "Network",
    "位置" to "Location",
    "相机" to "Camera",
    "麦克风" to "Microphone",
    "通讯录" to "Contacts",
    "电话" to "Phone",
    "电话状态" to "Phone status",
    "通话记录" to "Call log",
    "发送短信" to "Send SMS",
    "读取短信" to "Read SMS",
    "读取日历" to "Read calendar",
    "蓝牙" to "Bluetooth",
    "蓝牙连接" to "Bluetooth connection",
    "蓝牙扫描" to "Bluetooth scan",
    "附近 Wi-Fi 设备" to "Nearby Wi-Fi devices",
    "精确定位" to "Precise location",
    "粗略定位" to "Approximate location",
    "通知权限" to "Notification permission",
    "悬浮窗" to "Display over other apps",
    "使用情况访问" to "Usage access",
    "修改系统设置" to "Modify system settings",
    "忽略电池优化" to "Ignore battery optimization",
    "精确闹钟" to "Exact alarms",
    "权限设置" to "Permission settings",
    "通知权限可在系统的应用通知设置中开启。" to "Enable notifications in the system app notification settings.",
    "系统不允许应用直接授予通知使用权。请在“设置 > 通知 > 设备和应用通知”中允许 Mason。" to
        "Android does not let apps grant notification access directly. Allow MASON under Settings > Notifications > Device & app notifications.",
    "系统不提供普通授权弹窗。请在“特殊应用权限 > 显示在其他应用上层”中允许 Mason。" to
        "Android does not provide a standard permission dialog. Allow MASON under Special app access > Display over other apps.",
    "系统不提供普通授权弹窗。请在“特殊应用权限 > 使用情况访问权限”中允许 Mason。" to
        "Android does not provide a standard permission dialog. Allow MASON under Special app access > Usage access.",
    "系统不提供普通授权弹窗。请在“特殊应用权限 > 修改系统设置”中允许 Mason。" to
        "Android does not provide a standard permission dialog. Allow MASON under Special app access > Modify system settings.",
    "系统不提供普通授权弹窗。请在电池优化设置中将 Mason 设为不优化。" to
        "Android does not provide a standard permission dialog. Exclude MASON from battery optimization in system settings.",
    "系统不提供普通授权弹窗。请在“特殊应用权限 > 闹钟和提醒”中允许 Mason。" to
        "Android does not provide a standard permission dialog. Allow MASON under Special app access > Alarms & reminders.",
    "请在系统设置中为 Mason 开启这项高级权限。" to "Enable this advanced permission for MASON in system settings.",
    "前往设置" to "Open settings",
    "无障碍服务" to "Accessibility service",
    "记忆" to "Memory",
    "自定义记忆" to "Custom memory",
    "添加记忆" to "Add memory",
    "加入记忆" to "Add to memory",
    "更新记忆" to "Update memory",
    "名称" to "Name",
    "内容" to "Content",
    "标题" to "Title",
    "按敏感信息处理" to "Treat as sensitive information",
    "敏感内容已隐藏，点按可编辑" to "Sensitive content hidden. Tap to edit",
    "点击后填写名称和内容，例如车牌、常用地址、偏好说明" to
        "Tap to enter a name and content, such as a license plate, a frequently used address, or a preference",
    "例如：我的车牌 / 公司地址" to "e.g. My license plate / Company address",
    "填入希望 Mason 记住的文字" to "Enter the text you want MASON to remember",
    "默认隐藏内容，不主动进入模型上下文" to
        "Hidden by default and not proactively included in the model context",
    "开源协议" to "License",
    "项目地址" to "Project",
    "版本号" to "Version",
    "检查更新" to "Check for updates",
    "导出日志" to "Export logs",
    "导出诊断记录" to "Export diagnostic report",
    "清缓存" to "Clear cache",
    "缓存清理" to "Cache cleanup",
    "立即清理" to "Clear now",
    "重新扫描" to "Scan again",
    "关闭搜索" to "Close search",
    "打开菜单" to "Open menu",
    "打开模型配置" to "Open model settings",
    "打开设置项" to "Open setting",
    "关闭搜索" to "Close search",
    "编辑标题" to "Edit title",
    "隐藏" to "Hide",
    "显示" to "Show",
    "编辑模型配置" to "Edit model settings",
    "删除 Model ID" to "Delete Model ID",
    "当前" to "Current",
    "移除模型" to "Remove model",
    "删除模型" to "Delete model",
    "删除本地模型" to "Delete local model",
    "已选择" to "Selected",
    "当前主题色" to "Current theme color",
    "当前区域" to "Current region",
    "关闭表格" to "Close table",
    "下载表格" to "Download table",
    "全屏查看" to "View full screen",
    "停止屏幕助手" to "Stop screen assistant",
    "查看详情" to "View details",
    "查看执行详情" to "View execution details",
    "查看自动化详情" to "View automation details",
    "自动化" to "Automation",
    "自动化草稿" to "Automation draft",
    "创建自动化" to "Create automation",
    "更新自动化" to "Update automation",
    "恢复自动化" to "Resume automation",
    "暂停自动化" to "Pause automation",
    "删除自动化" to "Delete automation",
    "手动运行" to "Run manually",
    "运行时间" to "Run time",
    "执行内容" to "Action",
    "执行条件" to "Conditions",
    "安全说明" to "Safety note",
    "仅创建" to "Create only",
    "创建并测试" to "Create and test",
    "更新并测试" to "Update and test",
    "确认更新" to "Confirm update",
    "测试连接" to "Test connection",
    "测试中" to "Testing",
    "测试完成，请选择要添加的模型" to "Test complete. Select the models to add",
    "已测试通过并保存" to "Tested and saved",
    "拉取远程模型" to "Fetch remote models",
    "从当前接口读取可用模型" to "Read available models from this API",
    "添加另一个 Model ID" to "Add another Model ID",
    "手动添加的模型" to "Manually added model",
    "默认地址" to "Default address",
    "接口地址" to "API endpoint",
    "服务区域" to "Service region",
    "选择服务区域" to "Select service region",
    "可选" to "Optional",
    "需 Key" to "Key required",
    "Key 已配置" to "Key configured",
    "免费" to "Free",
    "未成功" to "Unsuccessful",
    "功能开关" to "Feature toggle",
    "记录" to "History",
    "屏幕助手日志" to "Screen assistant logs",
    "执行已暂停" to "Execution paused",
    "你通过悬浮窗暂停了屏幕助手" to "You paused the screen assistant from the overlay",
    "截图路径" to "Screenshot path",
    "宽度" to "Width",
    "高度" to "Height",
    "存储位置" to "Storage location",
    "节点数" to "Node count",
    "是否截断" to "Truncated",
    "执行方式" to "Execution method",
    "字符数" to "Character count",
    "持续时间" to "Duration",
    "不配置" to "Do not configure",
    "未安装" to "Not installed",
    "查看版本日志" to "View release notes",
    "更新" to "Updates",
    "查看近期提交和版本变化" to "View recent commits and version changes",
    "打开 GitHub Releases，查看是否有新版本" to
        "Open GitHub Releases to see whether a new version is available",
    "生成用于排查问题的脱敏文本，并打开系统分享" to "Generate a redacted diagnostic report and open the system share sheet",
    "清除缓存" to "Clear cache",
    "临时缓存" to "Temporary cache",
    "图片缩略图、临时文件和系统缓存" to "Image thumbnails, temporary files, and system cache",
    "运行缓存" to "Runtime cache",
    "Compose、WebView 或运行时生成的缓存" to "Cache generated by Compose, WebView, or the runtime",
    "崩溃记录" to "Crash records",
    "对话数据" to "Conversation data",
    "导出备份" to "Exported backups",
    "Downloads/mason 下的 Markdown 备份；由用户自行管理" to
        "Markdown backups in Downloads/mason; managed by you",
    "查看临时缓存、运行缓存和崩溃记录后再清理" to "Review temporary, runtime, and crash data before clearing it",
    "文件夹" to "Folder",
    "对话生成的文件夹" to "Folder generated by a conversation",
    "对话生成文件，可预览、编辑或分享" to "Conversation-generated file; preview, edit, or share it",
    "这个文件夹里暂时没有可预览的说明文件。" to "This folder has no previewable description file yet.",
    "暂时无法预览这个文件。" to "This file cannot be previewed right now.",
    "这个文件适合用本地软件打开预览。" to "Open this file in a local app to preview it.",
    "文件：" to "File: ",
    "路径：" to "Path: ",
    "清理" to "Clear",
    "正在扫描缓存..." to "Scanning cache...",
    "不会被本次清理删除" to "Not removed by this cleanup",
    "打开官方入口" to "Open official page",
    "网页登录" to "Web login",
    "登录" to "Sign in",
    "登录成功，工具已刷新" to "Signed in. Tools refreshed",
    "Mason 屏幕助手" to "MASON screen assistant",
    "操控中" to "Controlling",
    "停止操控" to "Stop control",
    "继续任务" to "Resume task",
    "取消任务" to "Cancel task",
    "查看图片" to "View image",
    "查看视频" to "View video",
    "播放音频" to "Play audio",
    "查看文档" to "View document",
    "查看文件" to "View file",
    "Mason 工具通知" to "MASON notifications",
    "Mason 发送的常规通知" to "Regular notifications from MASON",
    "Mason 任务实时状态" to "MASON live task updates",
    "用于 Android 16 的任务实时通知" to "Live task update notifications for Android 16",
    "本地模型下载" to "Local model downloads",
    "显示本地 AI 模型的后台下载进度" to "Background download progress for local AI models",
    "正在连接模型源" to "Connecting to the model source",
    "文件校验通过，可离线使用" to "File verified and ready for offline use",
    "打开 Mason 可继续下载" to "Open MASON to resume the download",
    "未完成文件已清理" to "Incomplete files were cleared",
    "正在进行 SHA-256 校验" to "Verifying SHA-256",
    "Mason 闹钟" to "MASON alarm",
    "闹钟" to "Alarm",
    "分享 Mason 回复" to "Share MASON response",
    "连接工具服务" to "Connect tool service",
    "工具扩展（MCP）" to "Tool extensions (MCP)",
    "其他已连接服务" to "Other connected services",
    "手动配置 MCP / A2A" to "Configure MCP / A2A manually",
    "暂无 MCP Server" to "No MCP servers",
    "暂无 A2A Agent" to "No A2A agents",
    "Agent Card 或服务地址" to "Agent Card or service URL",
    "Bearer Token（可选）" to "Bearer token (optional)",
    "移除连接" to "Remove connection",
    "暂无可接入官方通道" to "No official channels are available",
    "应用协作（A2A）" to "App collaboration (A2A)",
    "自定义 A2A 服务" to "Custom A2A service",
    "微信" to "WeChat",
    "支付宝" to "Alipay",
    "美团" to "Meituan",
    "语音通话" to "Voice calls",
    "视频通话" to "Video calls",
    "付款准备" to "Prepare a payment",
    "账单查询" to "Bill lookup",
    "生活服务" to "Local services",
    "外卖" to "Food delivery",
    "酒店" to "Hotels",
    "出行服务" to "Travel services",
    "地图" to "Maps",
    "办公系统" to "Office systems",
    "读取仓库" to "Read repositories",
    "管理 Issue" to "Manage issues",
    "处理 Pull Request" to "Handle pull requests",
    "消息与审批" to "Messages and approvals",
    "调用外部工具服务" to "Call external tool services",
    "查看仓库、Issue 和 Pull Request，并执行研发协作任务" to
        "View repositories, issues, and pull requests, and handle development collaboration tasks",
    "未安装；官方接入尚未开放" to "Not installed; official integration is not yet available",
    "官方接入尚未开放" to "Official integration is not yet available",
    "已安装，等待官方开放接入" to "Installed; waiting for official integration",
    "正在发现" to "Discovering",
    "MCP 配置不存在" to "MCP configuration not found",
    "A2A 配置不存在" to "A2A configuration not found",
    "需要登录授权" to "Sign-in required",
    "相关工具服务已配置但未启用" to "The related tool service is configured but disabled",
    "应用协作已授权" to "App collaboration authorized",
    "未完成授权" to "Authorization not completed",
    "MCP 配置已保存" to "MCP configuration saved",
    "MCP 已启用" to "MCP enabled",
    "MCP 已停用" to "MCP disabled",
    "MCP 配置已移除" to "MCP configuration removed",
    "MCP 连接检查完成" to "MCP connection check complete",
    "A2A 配置已保存" to "A2A configuration saved",
    "A2A Agent 已启用" to "A2A agent enabled",
    "A2A Agent 已停用" to "A2A agent disabled",
    "A2A 配置已移除" to "A2A configuration removed",
    "A2A Agent 检查完成" to "A2A agent check complete",
    "连接失败" to "Connection failed",
    "地址格式不正确" to "Invalid address format",
    "仅支持完整的 HTTP 或 HTTPS 地址" to "Only complete HTTP or HTTPS URLs are supported",
    "MCP 地址不正确" to "Invalid MCP URL",
    "A2A 地址不正确" to "Invalid A2A URL",
    "MCP tools/list 没有返回 result" to "MCP tools/list response is missing result",
    "MCP 调用失败" to "MCP call failed",
    "MCP 工具没有返回结果" to "The MCP tool returned no result",
    "MCP 工具执行失败" to "MCP tool execution failed",
    "MCP 初始化没有返回内容" to "MCP initialization returned no content",
    "MCP 初始化响应缺少 result" to "MCP initialization response is missing result",
    "远程协议调用失败" to "Remote protocol call failed",
    "该 MCP 服务没有返回 OAuth 登录信息" to "This MCP service did not provide OAuth sign-in information",
    "服务需要登录，但没有提供 OAuth 资源元数据" to "The service requires sign-in but did not provide OAuth resource metadata",
    "OAuth 资源元数据缺少授权服务器" to "The OAuth resource metadata is missing an authorization server",
    "授权服务器没有提供登录地址" to "The authorization server did not provide a sign-in URL",
    "授权服务器没有提供 Token 地址" to "The authorization server did not provide a token URL",
    "OAuth 响应中没有 access_token" to "The OAuth response is missing access_token",
    "OAuth 元数据格式不正确" to "Invalid OAuth metadata format",
    "该服务需要 OAuth Client ID；也可以改用访问令牌连接" to
        "This service requires an OAuth Client ID; you can also connect with an access token",
    "无法开始授权" to "Unable to start authorization",
    "授权回调校验失败，请重新连接" to "Authorization callback validation failed. Reconnect and try again",
    "授权回调中没有 code" to "The authorization callback has no code",
    "授权失败" to "Authorization failed",
    "A2A 没有返回内容" to "A2A returned no content",
    "A2A 任务失败" to "A2A task failed",
    "A2A 响应缺少 result" to "The A2A response is missing result",
    "A2A 返回了无法识别的任务格式" to "A2A returned an unrecognized task format",
    "A2A 任务目标不能为空" to "The A2A task goal cannot be empty",
    "外部 Agent 已完成任务" to "The external agent completed the task",
    "外部 Agent 已返回任务状态" to "The external agent returned a task status",
    "要交给外部 Agent 完成的具体目标" to "The specific goal for the external agent",
    "正在发送任务" to "Sending task",
    "能力" to "Capability",
    "能力未知" to "Capability unknown",
    "能力未知，可稍后重测" to "Capability unknown. Test again later",
    "连接可用" to "Connection available",
    "可连接服务" to "Connectable services",
    "扩展能力" to "Extended capabilities",
    "运行 Skill" to "Run Skill",
    "技能" to "Skills",
    "从 GitHub 安装 Skill" to "Install Skill from GitHub",
    "公开仓库或 Skill 目录链接" to "Public repository or Skill directory URL",
    "已安装技能，支持自主生成或从 GitHub 拉取" to "Installed skills can be generated or fetched from GitHub",
    "没有找到可用应用" to "No compatible app found",
    "选择编辑应用" to "Choose an app to edit",
    "选择打开应用" to "Choose an app to open",
    "文件不存在或无法打开" to "File does not exist or cannot be opened",
    "文件不存在或无法分享" to "File does not exist or cannot be shared",
    "无法打开链接" to "Unable to open link",
    "暂时没有可打开的文件" to "No files can be opened right now",
    "暂时没有可分享的文件" to "No files can be shared right now",
    "图片无法预览" to "Image cannot be previewed",
    "图片预览失败" to "Image preview failed",
    "表格已复制" to "Table copied",
    "表格下载失败" to "Table download failed",
    "代码已复制" to "Code copied",
    "图片内容为空" to "Image content is empty",
    "图片尺寸无效" to "Invalid image dimensions",
    "图片无法解码" to "Unable to decode the image",
    "需要存储权限才能保存图片" to "Storage permission is required to save the image",
    "图片超过 100 MB，无法直接预览" to "Images over 100 MB cannot be previewed directly",
    "SVG 图片超过 8 MB" to "SVG images over 8 MB are not supported",
    "SVG 图片无法预览" to "SVG image cannot be previewed",
    "文件不是 SVG 图片" to "The file is not an SVG image",
    "文件不是支持的图片格式" to "The file is not a supported image format",
    "文件异常" to "File error",
    "文件读取和处理" to "File reading and processing",
    "工具步骤已完成" to "Tool step completed",
    "工具执行失败" to "Tool execution failed",
    "工具完成后继续生成结果" to "Continue generating the result after the tool finishes",
    "工具执行已达到最大轮数，已停止以避免任务失控。" to "The tool reached the maximum number of rounds and stopped to prevent the task from running out of control.",
    "工具重试失败" to "Tool retry failed",
    "模型请求失败" to "Model request failed",
    "模型未返回可用内容" to "The model returned no usable content",
    "服务商限流，请稍后重试" to "The provider is rate limiting requests. Try again later",
    "请求超时，请检查网络或服务商响应" to "The request timed out. Check the network or provider response",
    "API Key 无效或没有权限" to "The API key is invalid or unauthorized",
    "接口地址或模型不存在" to "The API endpoint or model does not exist",
    "请求参数不被服务商接受" to "The provider rejected the request parameters",
    "请求失败" to "Request failed",
    "请先填写 API Key" to "Enter an API key first",
    "请先填写接口地址" to "Enter an API endpoint first",
    "请填写 API 地址" to "Enter an API URL",
    "请填写模型名称" to "Enter a model name",
    "当前模型需要 API Key，请先填写" to "The current model needs an API key. Enter it first",
    "当前模型需要 API Key，先去设置里填写" to "The current model needs an API key. Add it in Settings",
    "API Key 尚未验证，建议先测试连接" to "The API key has not been verified. Test the connection first",
    "启用功能需要配置模型" to "Configure a model to enable this feature",
    "免费模型仍需平台 Key，用来识别账号和限额" to "Free models still require a platform key to identify the account and limits",
    "暂无已生成自动化" to "No automations yet",
    "暂无运行记录" to "No run history",
    "暂无自动化" to "No automations",
    "没有写入自动化" to "No automation was written",
    "定时调度已生效" to "Scheduled automation is active",
    "自动化已保存" to "Automation saved",
    "自动化测试未通过" to "Automation test failed",
    "动态选择使用模型" to "Choose models dynamically",
    "根据任务难度，在已配置的模型中自动选择模型" to
        "Automatically choose a configured model based on task difficulty",
    "发送、删除、写入和修改系统状态前进行确认" to
        "Ask for confirmation before sending, deleting, writing, or changing system state",
    "自动化后台运行" to "Run automations in the background",
    "定时自动化可由系统在后台拉起执行" to
        "Scheduled automations can be started by the system in the background",
    "未授权" to "Not authorized",
    "编辑标题" to "Edit title",
    "隐藏" to "Hide",
    "显示" to "Show",
    "当前" to "Current",
    "编辑模型配置" to "Edit model settings",
    "删除 Model ID" to "Delete Model ID",
    "移除模型" to "Remove model",
    "删除模型" to "Delete model",
    "停止屏幕助手" to "Stop screen assistant",
    "关闭导航菜单" to "Close navigation menu",
    "石头屏幕助手" to "Stone screen assistant",
    "草稿生成失败" to "Failed to generate the draft",
    "需要用户补充信息" to "More information is required",
    "等待用户继续" to "Waiting for the user to continue",
    "等待用户确认" to "Waiting for user confirmation",
    "确认后继续生成结果" to "Continue generating the result after confirmation",
    "确认前不会创建或运行；后台运行受设置总开关控制" to "Nothing is created or run before confirmation. Background runs follow the master setting",
    "确认前不会创建或运行；后台运行受设置总开关控制" to "Nothing is created or run before confirmation. Background runs follow the master setting",
    "断开后需要重新扫码配对。" to "Scan again to pair after disconnecting.",
    "电脑端已断开，本机信息清理失败，请重试" to "The computer disconnected, but local cleanup failed. Try again",
    "远端电脑连接已失效" to "The remote computer connection is no longer valid",
    "电脑端暂无可用 Skill" to "No Skills are available on the computer",
    "电脑端 Codex 暂不支持这项操作" to "Computer Codex does not support this operation yet",
    "电脑任务已经结束" to "The computer task has ended",
    "电脑上的文件已移动或不可用" to "The file on the computer was moved or is unavailable",
    "无法读取电脑端会话" to "Unable to read computer conversations",
    "无法加载电脑对话" to "Unable to load the computer conversation",
    "无法发送到电脑" to "Unable to send to the computer",
    "无法停止电脑任务" to "Unable to stop the computer task",
    "电脑端附件暂存空间已满" to "The computer attachment storage is full",
    "单个附件不能超过 20 MB" to "Each attachment must be 20 MB or smaller",
    "每次最多添加 5 个附件" to "You can add up to 5 attachments at a time",
    "从电脑读取并预览" to "Read and preview from the computer",
    "电脑文件" to "Computer file",
    "向电脑 Codex 发起对话" to "Start a conversation with computer Codex",
    "向电脑 Codex 发送消息" to "Send a message to computer Codex",
    "电脑 Codex 正在执行" to "Computer Codex is running",
    "向电脑端发起对话" to "Start a conversation with the computer",
    "向电脑端发送消息" to "Send a message to the computer",
    "请处理这些材料" to "Please process these materials",
    "跟MASON聊聊" to "Chat with MASON",
    "我们应该先做什么？" to "What should we do first?",
    "今天想试点不一样的吗？" to "Want to try something different today?",
    "一起把想法做出来。" to "Let's build the idea together.",
    "卡住的事，也可以从这里开始。" to "You can start here when something feels stuck.",
    "现在最值得解决的问题是什么？" to "What is most worth solving right now?",
    "Mason 附加上下文" to "Mason additional context",
    "没有匹配的对话" to "No matching conversations",
    "最近对话" to "Recent conversations",
    "最近" to "Recent",
    "置顶" to "Pinned",
    "取消置顶" to "Unpin",
    "归档" to "Archive",
    "删除选中对话" to "Delete selected conversations",
    "退出 MASON？" to "Exit MASON?",
    "退出" to "Exit",
    "风险确认：" to "Risk confirmation: ",
    "高风险操作确认" to "High-risk action confirmation",
    "敏感参数不会写入对话，请在 Skill 的安全配置中提供。" to "Sensitive parameters are not written to the conversation. Provide them in the Skill's secure configuration.",
    "未调用模型，本轮由 Mason 本地逻辑完成" to "No model was called. This turn was completed by MASON's local logic",
    "未调用模型，本回答由 Mason 本地逻辑完成" to "No model was called. This answer was completed by MASON's local logic",
    "该回答生成于模型记录功能启用前" to "This answer was generated before model attribution was enabled",
    "待连接" to "Waiting for connection",
    "未安装" to "Not installed",
    "等待官方接入" to "Waiting for official access",
    "需要连接" to "Connection required",
    "已被新消息打断" to "Interrupted by a new message",
    "用户停止生成" to "The user stopped generation",
    "用户取消任务" to "The user canceled the task",
    "用户暂停任务" to "The user paused the task",
    "用户已拒绝" to "The user declined",
    "用户已确认，正在执行工具" to "The user confirmed. Running the tool",
    "安全说明" to "Safety note",
    "文件不存在或已被移动" to "The file does not exist or was moved",
    "图片不存在或已被移动" to "The image does not exist or was moved",
    "图片路径不受信任" to "The image path is not trusted",
    "图片大小无效" to "Invalid image size",
    "SVG 不允许外部实体" to "External entities are not allowed in SVG",
    "SVG 不允许外部样式" to "External styles are not allowed in SVG",
    "SVG 缺少根节点" to "The SVG has no root element",
    "SVG 预览视口尺寸无效" to "Invalid SVG preview viewport size",
    "正在打开相机…" to "Opening camera…",
    "系统设置" to "System settings",
    "返回 Mason 查看模型测试结果" to "Return to MASON to view model test results",
    "返回 Mason 选择要添加的模型" to "Return to MASON to select models to add",
    "通知权限未授予，通知模式已保存，但暂时无法发送通知" to "Notification permission was not granted. The mode was saved, but notifications cannot be sent yet",
    "诊断记录不存在" to "The diagnostic report does not exist",
    "Mason 诊断记录" to "MASON diagnostic report",
    "分享 Mason 诊断记录" to "Share MASON diagnostic report",
    "未知错误" to "Unknown error",
    "无法写入图片" to "Unable to write the image",
    "诊断日志已导出" to "Diagnostic report exported",
    "信息确认" to "Confirm",
    "只清理图片预览缓存" to "Only clear image preview cache",
    "缓存清理失败" to "Unable to clear cache",
    "图片预览缓存已清理" to "Image preview cache cleared",
    "仅安卓12+生效" to "Requires Android 12 or newer",
    "仅安卓13+生效" to "Requires Android 13 or newer",
    // Additional host UI copy used by the chat, workbench, integrations, and
    // permission surfaces. Keep these in the shared table so both MASON and
    // the embedded Remote module switch together.
    "删除这条记忆？" to "Delete this memory?",
    "删除产出？" to "Delete artifact?",
    "删除模型？" to "Delete model?",
    "删除 Model ID？" to "Delete Model ID?",
    "删除后无法恢复。" to "This cannot be undone.",
    "删除后不能恢复。" to "This cannot be undone.",
    "当前模型需要 API Key" to "The current model needs an API key",
    "当前配置尚未测试通过，退出后本次填写的内容不会保存。" to
        "This configuration has not passed testing. Your changes will not be saved if you exit.",
    "使用 Skill" to "Use Skill",
    "暂无已安装技能" to "No installed skills",
    "选择后会作为本轮对话的执行偏好发送给 Mason。" to
        "Your selection will be sent to MASON as the execution preference for this conversation.",
    "请求" to "Request",
    "响应" to "Response",
    "执行过程" to "Execution",
    "执行代码" to "Run code",
    "命令执行" to "Command execution",
    "命令输出" to "Command output",
    "命令详情" to "Command details",
    "工具调用" to "Tool call",
    "调用工具" to "Call tool",
    "工具结果" to "Tool result",
    "其他操作" to "Other action",
    "查看步骤" to "View steps",
    "测试到此步骤" to "Test through this step",
    "添加步骤" to "Add step",
    "编辑步骤" to "Edit step",
    "移除步骤" to "Remove step",
    "添加服务商" to "Add provider",
    "手动配置" to "Manual configuration",
    "手动配置 MCP / A2A" to "Configure MCP / A2A manually",
    "名称" to "Name",
    "访问令牌" to "Access token",
    "保存并检查" to "Save and check",
    "检查连接" to "Check connection",
    "编辑" to "Edit",
    "配置" to "Configure",
    "待登录" to "Sign-in required",
    "待检查" to "Needs checking",
    "尚未检查" to "Not checked",
    "已保存，等待检查" to "Saved; waiting for check",
    "连接工具服务" to "Connect tool service",
    "扩展能力" to "Extended capabilities",
    "应用协作（A2A）" to "App collaboration (A2A)",
    "让 Mason 把任务交给具体 App。首次连接在目标 App 内授权，发送消息、通话或支付等操作仍会再次确认。" to
        "Let MASON hand tasks to a specific app. Authorize the first connection in the target app; sending messages, calls, and payments still require confirmation.",
    "自定义 A2A 服务" to "Custom A2A services",
    "可接收 Mason 委派的任务" to "Can receive tasks delegated by MASON",
    "工具扩展（MCP）" to "Tool extensions (MCP)",
    "给 Mason 增加搜索、文件、地图、GitHub 和办公系统等工具。连接后，实际调用仍受工具确认和审计保护。" to
        "Add search, file, map, GitHub, and office tools to MASON. Actual calls remain protected by confirmation and audit controls.",
    "可连接服务" to "Connectable services",
    "其他已连接服务" to "Other connected services",
    "当前未连接工具服务。支持的用途：" to "No tool service is connected. Supported uses: ",
    "代码仓库和研发协作" to "Code repositories and development collaboration",
    "地图和位置服务" to "Maps and location services",
    "文件读取和处理" to "File reading and processing",
    "联网搜索和资料检索" to "Web search and research",
    "办公系统协作" to "Office system collaboration",
    "为 Mason 提供外部工具" to "Provide external tools to MASON",
    "连接任务" to "Connect task",
    "允许应用协作？" to "Allow app collaboration?",
    "高风险操作确认" to "High-risk action confirmation",
    "低风险" to "Low risk",
    "需要确认" to "Confirmation required",
    "高风险" to "High risk",
    "这次只允许当前任务。以后再次委派敏感操作时，Mason 仍会询问。" to
        "This allows only the current task. MASON will ask again before a sensitive delegated action.",
    "这次只允许当前任务。以后再次保存敏感信息时，Mason 仍会询问。" to
        "This allows only the current task. MASON will ask again before saving sensitive information.",
    "允许一次只继续本轮任务；总是允许会记住该工具，之后可在设置中撤销。" to
        "Allow once continues only this task; Always allow remembers this tool and can be revoked in Settings.",
    "令牌只保存在本机加密存储中。" to "Tokens are stored only in encrypted storage on this device.",
    "GitHub 不允许客户端自动注册。请输入你创建的 OAuth App Client ID，Mason 将使用 PKCE 完成登录。" to
        "GitHub does not allow clients to register OAuth apps automatically. Enter the OAuth App Client ID you created; MASON will sign in with PKCE.",
    "打开登录" to "Open sign-in",
    "等待连接检查" to "Waiting for connection check",
    "连接中" to "Connecting",
    "在线" to "Online",
    "异常" to "Error",
    "已完成目标识别和回答规划" to "Goal recognition and answer planning complete",
    "正在组织回复..." to "Composing reply...",
    "暂无内容" to "No content",
    "定时调度已生效" to "Scheduled automation is active",
    "自动化已保存" to "Automation saved",
    "自动化测试未通过" to "Automation test failed",
    "自动化已完成" to "Automation completed",
    "手动自动化" to "Manual automation",
    "输入变量" to "Input variable",
    "输出变量" to "Output variable",
    "条件变量" to "Condition variable",
    "条件操作" to "Condition operator",
    "条件值" to "Condition value",
    "触发类型" to "Trigger type",
    "触发值" to "Trigger value",
    "每周" to "Weekly",
    "每天" to "Daily",
    "小时（0-23）" to "Hour (0-23)",
    "分钟（0-59）" to "Minute (0-59)",
    "无额外条件" to "No additional conditions",
    "充电时" to "While charging",
    "电量充足" to "Battery not low",
    "需要联网" to "Internet required",
    "仅非计费网络" to "Unmetered network only",
    "运行时读取今日日历，需要日历读取权限；确认前不会创建或运行" to
        "Reading today's calendar at run time requires calendar permission; nothing is created or run before confirmation",
    "确认前不会创建或运行；后台运行受设置总开关控制" to
        "Nothing is created or run before confirmation; background runs follow the master switch",
    "去下载" to "Download",
    "下载完成" to "Download complete",
    "下载失败，可继续重试" to "Download failed; you can retry",
    "等待下载" to "Waiting for download",
    "正在检查下载条件" to "Checking download requirements",
    "正在校验文件" to "Verifying file",
    "安装中..." to "Installing...",
    "安装" to "Install",
    "未开启" to "Not enabled",
    "未发布" to "Not published",
    "基础对话" to "Basic chat",
    "重度任务模型" to "Heavy-task model",
    "轻度任务模型" to "Light-task model",
    "视觉" to "Vision",
    "网络搜索" to "Web search",
    "文本" to "Text",
    "多模态" to "Multimodal",
    "无识图" to "No vision",
    "无生图" to "No image generation",
    "缺少 API Key" to "API key missing",
    "缺少 Model ID" to "Model ID missing",
    "OpenAI 兼容接口" to "OpenAI-compatible API",
    "使用你填写的 OpenAI 兼容接口。" to "Use the OpenAI-compatible API you entered.",
    "本地模型不需要 API Key，模型文件和推理都保留在设备上。" to
        "Local models do not need an API key; model files and inference stay on this device.",
    "地址" to "Address",
    "设备 ID" to "Device ID",
    "证书指纹" to "Certificate fingerprint",
    "确认连接这台电脑" to "Confirm connection to this computer",
    "确认替换" to "Confirm replacement",
    "确认重试" to "Confirm retry",
    "取消测试" to "Cancel test",
    "取消测试？" to "Cancel test?",
    "设备已配对" to "Device paired",
    "无法完成配对" to "Unable to complete pairing",
    "正在安全连接" to "Establishing a secure connection",
    "完成" to "Done",
    "需要相机权限才能扫描电脑上的配对二维码" to "Camera permission is required to scan the pairing QR code on the computer",
    "将电脑上的配对二维码放入框内" to "Place the computer's pairing QR code inside the frame",
    "处理请求" to "Process request",
    "正在处理请求" to "Processing request",
    "上下文压缩中" to "Compressing context",
    "压缩上下文" to "Compress context",
    "内容较长，已截取最近详情。" to "Long content; showing the latest details.",
    "此对话没有可显示的文字消息" to "This conversation has no displayable text messages",
    "仅显示最近" to "Showing only the latest",
    "下载表格" to "Download table",
    "关闭表格" to "Close table",
    "表格" to "Table",
    "表格已复制" to "Table copied",
    "表格下载失败" to "Table download failed",
    "代码已复制" to "Code copied",
    "代码" to "Code",
    "终端 Web 搜索" to "Terminal web search",
    "无参数" to "No arguments",
    "待测试能力" to "Capabilities not tested",
    "已记住的工具授权" to "Remembered tool permissions",
    "身份" to "Identity",
    "身份证" to "ID card",
    "小区" to "Residential complex",
    "车牌" to "License plate",
    "相册" to "Gallery",
    "文档" to "Document",
    "下载" to "Download",
    "图片" to "Image",
    "文件" to "File",
    "本机" to "This device",
    "本地" to "Local",
    "纯黑" to "Black",
    "白天" to "Day",
    "夜间" to "Night",
    "一" to "Mon",
    "二" to "Tue",
    "三" to "Wed",
    "四" to "Thu",
    "五" to "Fri",
    "六" to "Sat",
    "日" to "Sun",
)

private val HOST_PHRASE_REPLACEMENTS = listOf(
    "当前模型：" to "Current model: ",
    "聊天模型" to "Chat model",
    "识图模型" to "Vision model",
    "生图模型" to "Image model",
    "本地备用" to "Local fallback",
    "模型参与：" to "Models involved: ",
    "工具结果 · " to "Tool result · ",
    "模式 · " to "Mode · ",
    "离线 · " to "Offline · ",
    "已连接 · " to "Connected · ",
    "电脑文件 · " to "Computer file · ",
    "产出 · " to "Artifact · ",
    "来源 " to "Source ",
    "已选 " to "Selected ",
    "已下载 " to "Downloaded ",
    "已安装 " to "Installed ",
    "已启用 " to "Enabled ",
    "已停用 " to "Disabled ",
    "已删除 " to "Deleted ",
    "已更新 " to "Updated ",
    "已创建 " to "Created ",
    "已保存到 " to "Saved to ",
    "已下载到 " to "Downloaded to ",
    "正在下载：" to "Downloading: ",
    "保存失败：" to "Save failed: ",
    "分享失败：" to "Share failed: ",
    "打开失败：" to "Open failed: ",
    "打开分享面板失败：" to "Unable to open the share sheet: ",
    "读取预览失败：" to "Preview read failed: ",
    "图片读取失败：" to "Image read failed: ",
    "图片无法分享：" to "Unable to share the image: ",
    "发送失败：" to "Send failed: ",
    "删除失败：" to "Delete failed: ",
    "导出成功：" to "Exported: ",
    "导出失败：" to "Export failed: ",
    "导入成功：" to "Imported: ",
    "导入失败：" to "Import failed: ",
    "刷新失败：" to "Refresh failed: ",
    "连接失败：" to "Connection failed: ",
    "无法创建" to "Unable to create",
    "无法打开" to "Unable to open",
    "无法读取" to "Unable to read",
    "无法写入" to "Unable to write",
    "无法完成" to "Unable to complete",
    "无法连接" to "Unable to connect",
    "无法发送" to "Unable to send",
    "点击重试" to "Tap to retry",
    "第 " to "Attempt ",
    " 次尝试" to "",
    "每次最多添加 " to "You can add up to ",
    " 个附件" to " attachments",
    "单个附件不能超过 " to "Each attachment must be no larger than ",
    "每 " to "Every ",
    " 分钟" to " minutes",
    "每天 " to "Daily at ",
    "连接 WiFi：" to "Connect to Wi-Fi: ",
    "连接蓝牙：" to "Connect to Bluetooth: ",
    "收到通知：" to "Notification received: ",
    "连接工具服务" to "Connect tool service",
    "当前服务商暂无可识图模型" to "The current provider has no vision models",
    "当前服务商暂无生图模型" to "The current provider has no image-generation models",
    "尚未配置" to "Not configured",
    "删除后无法恢复。" to "This cannot be undone.",
    "请检查网络或服务商响应" to "Check the network or provider response",
    "请在设置里测试连接" to "Test the connection in Settings",
    "应用：" to "App: ",
)

private fun translateHostPhrases(value: String): String =
    HOST_PHRASE_REPLACEMENTS
        .sortedByDescending { (source, _) -> source.length }
        .fold(value) { result, (source, target) -> result.replace(source, target) }

private fun translateKnownText(value: String): String {
    val exact = translateExactText(value)
    return if (exact != value) exact else translateHostPhrases(value)
}

private fun translateExactText(value: String): String {
    HOST_ENGLISH_TEXT[value]?.let { return it }
    return remoteEnglishText(value)
}

/**
 * Integration screens contain a small amount of runtime-generated copy. Keep
 * the protocol/data models language-neutral and translate that copy only at
 * the display boundary. User-provided names, URLs, and remote descriptions are
 * preserved unless they are one of the known app-owned labels below.
 */
private fun translateIntegrationText(value: String): String? {
    fun token(raw: String): String = translateExactText(raw).takeIf { it != raw } ?: raw

    fun listPart(raw: String): String {
        val exact = token(raw)
        if (exact != raw) return exact
        val nested = raw.split("、")
        if (nested.size < 2) return raw
        val translated = nested.map(::token)
        return translated
            .takeIf { translated.indices.any { index -> translated[index] != nested[index] } }
            ?.joinToString(", ")
            ?: raw
    }

    fun translatedList(raw: String, separator: String, englishSeparator: String): String? {
        val parts = raw.split(separator)
        if (parts.size < 2) return null
        val translated = parts.map(::listPart)
        return translated
            .takeIf { translated.indices.any { index -> translated[index] != parts[index] } }
            ?.joinToString(englishSeparator)
    }

    val usesPrefix = "当前未连接工具服务。支持的用途："
    if (value.startsWith(usesPrefix) && value.endsWith("。")) {
        val uses = value.removePrefix(usesPrefix).removeSuffix("。")
        val translatedUses = uses.split("、").map(::token).joinToString(", ")
        return "No tool service is connected. Supported uses: $translatedUses."
    }

    val handoffPrefix = "把当前任务交给 "
    if (value.startsWith(handoffPrefix)) {
        val remainder = value.removePrefix(handoffPrefix)
        val skillsMarker = " 处理，可使用："
        if (skillsMarker in remainder) {
            val agentName = remainder.substringBefore(skillsMarker)
            val skills = remainder.substringAfter(skillsMarker)
                .split("、")
                .joinToString(", ", transform = ::token)
            return "Hand the current task to $agentName; available skills: $skills"
        }
        if (remainder.endsWith(" 处理")) {
            return "Hand the current task to ${remainder.removeSuffix(" 处理")}"
        }
    }

    val delegationPrefix = "将任务委派给 "
    if (value.startsWith(delegationPrefix)) {
        val remainder = value.removePrefix(delegationPrefix)
        val capabilitiesMarker = "。能力："
        val summary = remainder.substringBefore(capabilitiesMarker)
        val separatorIndex = summary.indexOf(": ")
        if (separatorIndex > 0) {
            val agentName = summary.substring(0, separatorIndex)
            val description = summary.substring(separatorIndex + 2)
            val capabilities = if (capabilitiesMarker in remainder) {
                remainder.substringAfter(capabilitiesMarker)
                    .split("、")
                    .joinToString(", ", transform = ::token)
            } else {
                ""
            }
            return buildString {
                append("Delegate the task to ")
                append(agentName)
                append(": ")
                append(description)
                if (capabilities.isNotBlank()) {
                    append(". Capabilities: ")
                    append(capabilities)
                }
            }
        }
    }

    if (value.startsWith("允许 ") && value.endsWith(" 工具处理本轮任务")) {
        val subject = value.removePrefix("允许 ").removeSuffix(" 工具处理本轮任务")
        val separatorIndex = subject.indexOf(" 的 ")
        if (separatorIndex > 0) {
            val serverName = subject.substring(0, separatorIndex)
            val toolName = subject.substring(separatorIndex + 3)
            return "Allow the $toolName tool from $serverName to handle this task"
        }
    }

    if (value.startsWith("已交给 ") && value.endsWith(" 处理")) {
        return "Handed off to ${value.removePrefix("已交给 ").removeSuffix(" 处理")}"
    }
    if (value.startsWith("委派给 ")) {
        return "Delegated to ${value.removePrefix("委派给 ")}"
    }
    Regex("^MCP (.+) 没有返回内容$").matchEntire(value)?.let { match ->
        return "MCP ${match.groupValues[1]} returned no content"
    }
    if (value.startsWith("OAuth 换取 Token 失败：")) {
        return "OAuth token exchange failed: ${value.removePrefix("OAuth 换取 Token 失败：")}"
    }
    if (value.startsWith("读取 OAuth 元数据失败：")) {
        return "Failed to read OAuth metadata: ${value.removePrefix("读取 OAuth 元数据失败：")}"
    }

    translatedList(value, " · ", " · ")?.let { return it }
    translatedList(value, "、", ", ")?.let { return it }

    val providerNames = setOf("微信", "支付宝", "美团")
    if (value.startsWith("连接") && value.length > 2) {
        val provider = value.removePrefix("连接")
        if (provider in providerNames) return "Connect ${token(provider)}"
    }
    if (value.startsWith("连接 ") && !value.contains("：")) {
        val target = value.removePrefix("连接 ")
        if (target == "GitHub" || target in providerNames) return "Connect ${token(target)}"
    }
    if (value.startsWith("等待连接 ")) {
        return "Waiting for ${token(value.removePrefix("等待连接 "))} connection"
    }
    if (value.endsWith(" 连接配置已保存")) {
        return "${value.removeSuffix(" 连接配置已保存")} connection configuration saved"
    }
    if (value.endsWith(" 已配置，但连接尚未就绪")) {
        return "${value.removeSuffix(" 已配置，但连接尚未就绪")} is configured, but the connection is not ready"
    }
    if (value.startsWith("尚未连接可用的") && value.endsWith("工具服务")) {
        val name = value.removePrefix("尚未连接可用的").removeSuffix("工具服务")
        return "No available ${token(name)} tool service is connected"
    }
    Regex("^(.*) (\\d+) 项能力$").matchEntire(value)?.let { match ->
        val prefix = match.groupValues[1]
        val count = match.groupValues[2]
        return "$prefix $count ${if (count == "1") "capability" else "capabilities"}"
    }
    Regex("^(.*) (\\d+) 个工具$").matchEntire(value)?.let { match ->
        val prefix = match.groupValues[1]
        val count = match.groupValues[2]
        return "$prefix $count ${if (count == "1") "tool" else "tools"}"
    }
    return null
}

val LocalRemoteStrings = staticCompositionLocalOf {
    RemoteStrings(RemoteResolvedLanguage.CHINESE)
}

/**
 * Material3 owns a few accessibility labels (for example, "Close sheet") and
 * resolves them from the Android configuration rather than from Compose text.
 * Keep that configuration in sync with the in-app language preference.
 */
@Composable
fun ProvideRemoteLocale(content: @Composable () -> Unit) {
    val strings = LocalRemoteStrings.current
    if (!strings.isEnglish) {
        content()
        return
    }
    val context = LocalContext.current
    val englishConfiguration = remember(context) {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.ENGLISH)
        configuration
    }
    CompositionLocalProvider(
        // Keep LocalContext as the Activity. Hilt's navigation factory relies
        // on that identity; only Material/Compose configuration consumers need
        // the in-app locale override here.
        LocalConfiguration provides englishConfiguration,
        content = content,
    )
}

/**
 * Provides the in-app language to Android resources used by Compose components
 * that create their own window (for example, Material3 bottom sheets).
 *
 * This must stay scoped to the component that opens the auxiliary window. The
 * application root still exposes the real Activity context to navigation and
 * Hilt, while the auxiliary window gets resources for the selected language.
 */
@Composable
fun WithRemoteMaterialResources(content: @Composable () -> Unit) {
    val strings = LocalRemoteStrings.current
    if (!strings.isEnglish) {
        content()
        return
    }
    val context = LocalContext.current
    val englishContext = remember(context) {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.ENGLISH)
        ContextThemeWrapper(context, 0).apply {
            applyOverrideConfiguration(configuration)
        }
    }
    CompositionLocalProvider(
        LocalContext provides englishContext,
        LocalConfiguration provides englishContext.resources.configuration,
        LocalView provides remember(englishContext) { View(englishContext) },
        content = content,
    )
}

@Composable
fun resolveRemoteStrings(preference: RemoteLanguagePreference): RemoteStrings {
    val systemLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty()
    return resolveRemoteStrings(preference, systemLanguage)
}

/** Resolve strings outside Compose, such as notifications and system overlays. */
fun resolveRemoteStrings(
    preference: RemoteLanguagePreference,
    systemLanguage: String,
): RemoteStrings {
    val resolved = when (preference) {
        RemoteLanguagePreference.CHINESE -> RemoteResolvedLanguage.CHINESE
        RemoteLanguagePreference.ENGLISH -> RemoteResolvedLanguage.ENGLISH
        RemoteLanguagePreference.SYSTEM -> if (systemLanguage.startsWith("zh", ignoreCase = true)) {
            RemoteResolvedLanguage.CHINESE
        } else {
            RemoteResolvedLanguage.ENGLISH
        }
    }
    return RemoteStrings(resolved)
}

internal fun RemoteLanguagePreference.localizedLabel(strings: RemoteStrings): String = when (this) {
    RemoteLanguagePreference.SYSTEM -> strings.text("跟随系统", "Follow system")
    RemoteLanguagePreference.CHINESE -> strings.text("中文", "Chinese")
    RemoteLanguagePreference.ENGLISH -> "English"
}

internal fun RemoteThemeMode.localizedLabel(strings: RemoteStrings): String = when (this) {
    RemoteThemeMode.SYSTEM -> strings.text("跟随系统", "Follow system")
    RemoteThemeMode.LIGHT -> strings.text("浅色", "Light")
    RemoteThemeMode.DARK -> strings.text("深色", "Dark")
}

internal fun RemoteInterfaceStyle.localizedLabel(strings: RemoteStrings): String = when (this) {
    RemoteInterfaceStyle.NATIVE -> strings.text("原生", "Native")
    RemoteInterfaceStyle.GLASS -> strings.text("玻璃", "Glass")
}

internal fun RemoteFontSizePreference.localizedLabel(strings: RemoteStrings): String = when (this) {
    RemoteFontSizePreference.SMALL -> strings.text("小", "Small")
    RemoteFontSizePreference.MEDIUM -> strings.text("中", "Medium")
    RemoteFontSizePreference.LARGE -> strings.text("大", "Large")
    RemoteFontSizePreference.EXTRA_LARGE -> strings.text("超大", "Extra large")
}

internal fun TaskNotificationMode.localizedLabel(strings: RemoteStrings): String = when (this) {
    TaskNotificationMode.REGULAR -> strings.text("启用常规通知", "Enable regular notifications")
    TaskNotificationMode.ISLAND -> strings.text("启用岛通知", "Enable island notifications")
    TaskNotificationMode.DISABLED -> strings.text("不启用", "Disabled")
}

internal fun TaskNotificationMode.localizedSummary(strings: RemoteStrings): String = when (this) {
    TaskNotificationMode.REGULAR -> strings.text("常规通知", "Regular notifications")
    TaskNotificationMode.ISLAND -> strings.text("岛通知", "Island notifications")
    TaskNotificationMode.DISABLED -> strings.text("不启用", "Disabled")
}

internal fun RemoteMessageSendMode.localizedLabel(strings: RemoteStrings): String = when (this) {
    RemoteMessageSendMode.STEER -> strings.text("插队", "Send now")
    RemoteMessageSendMode.QUEUE -> strings.text("排队", "Queue")
}

internal fun remoteEnglishText(value: String): String = when (value) {
    "外观" -> "Appearance"
    "通知" -> "Notifications"
    "对话" -> "Conversations"
    "远程配置" -> "Remote connection"
    "系统" -> "System"
    "设置" -> "Settings"
    "关于" -> "About"
    "折射效果" -> "Refraction"
    "透明度" -> "Transparency"
    "输入透明度" -> "Enter transparency"
    "霜冻" -> "Frost"
    "输入霜冻强度" -> "Enter frost strength"
    "任务通知" -> "Task notifications"
    "进行中发送消息" -> "Messages during a task"
    "深色模式" -> "Dark mode"
    "字体大小" -> "Font size"
    "语言" -> "Language"
    "风格" -> "Style"
    "启用常规通知" -> "Enable regular notifications"
    "启用岛通知" -> "Enable island notifications"
    "常规通知" -> "Regular notifications"
    "岛通知" -> "Island notifications"
    "不启用" -> "Disabled"
    "插队" -> "Send now"
    "排队" -> "Queue"
    "跟随系统" -> "Follow system"
    "浅色" -> "Light"
    "深色" -> "Dark"
    "原生" -> "Native"
    "玻璃" -> "Glass"
    "小" -> "Small"
    "中" -> "Medium"
    "大" -> "Large"
    "超大" -> "Extra large"
    "当前选项" -> "Current option"
    "当前模式" -> "Current mode"
    "当前字体大小" -> "Current font size"
    "当前语言" -> "Current language"
    "当前风格" -> "Current style"
    "已配对（$value）" -> value
    "远端电脑配置" -> "Remote computer"
    "管理" -> "Manage"
    "版本号" -> "Version"
    "项目地址" -> "Project"
    "开源协议" -> "License"
    "检查更新" -> "Check for updates"
    "导出日志" -> "Export logs"
    "清缓存" -> "Clear cache"
    "信息确认" -> "Confirm"
    "只清理图片预览缓存" -> "Only image preview cache will be cleared"
    "取消" -> "Cancel"
    "清理" -> "Clear"
    "诊断日志已导出" -> "Diagnostic log exported"
    "未知错误" -> "Unknown error"
    "图片预览缓存已清理" -> "Image preview cache cleared"
    "缓存清理失败" -> "Unable to clear cache"
    "仅安卓12+生效" -> "Requires Android 12 or newer"
    "仅安卓13+生效" -> "Requires Android 13 or newer"
    "收起选项" -> "Collapse options"
    "展开选项" -> "Expand options"
    "打开设置项" -> "Open setting"
    "未配对远端电脑" -> "No remote computer paired"
    "电脑上暂无会话" -> "No conversations on the computer"
    "置顶" -> "Pinned"
    "最近" -> "Recent"
    "点击重试" -> ", tap to retry"
    "断开中" -> "Disconnecting"
    "连接中" -> "Connecting"
    "已连接" -> "Connected"
    "已断开" -> "Disconnected"
    "连接状态" -> "Connection status"
    "新建远端对话" -> "New remote conversation"
    "新对话" -> "New conversation"
    "正在读取电脑选项" -> "Reading computer options"
    "项目" -> "Project"
    "选择项目" -> "Select project"
    "模型" -> "Model"
    "选择模型" -> "Select model"
    "推理层级" -> "Reasoning"
    "访问权限" -> "Permissions"
    "选择权限" -> "Select permissions"
    "向电脑端发起对话" -> "Start a conversation with the computer"
    "发起对话" -> "Start conversation"
    "当前任务进行中，可插入或排队" -> "A task is running; send now or queue"
    "添加" -> "Add"
    "添加图片" -> "Add image"
    "添加文件" -> "Add file"
    "使用 Skill" -> "Use skill"
    "正在读取远程 Skill" -> "Reading remote skills"
    "远程端暂无可用 Skill" -> "No remote skills available"
    "停止" -> "Stop"
    "发送" -> "Send"
    "移除" -> "Remove"
    "拒绝" -> "Decline"
    "允许" -> "Allow"
    "回到最新消息" -> "Jump to latest message"
    "加载中" -> "Loading"
    "还没有消息" -> "No messages yet"
    "等待确认" -> "Awaiting confirmation"
    "确定" -> "Confirm"
    "取消置顶" -> "Unpin"
    "归档" -> "Archive"
    "重试" -> "Retry"
    "返回" -> "Back"
    "确认" -> "Confirm"
    "电脑端启动后扫码配对" -> "Scan the QR code after starting the computer app"
    "请选择连接方式" -> "Choose a connection method"
    "Cloudflare 隧道" -> "Cloudflare tunnel"
    "本地网络" -> "Local network"
    "中转，电脑端重启需要重新配对" -> "Relay; pairing is required after restarting the computer app"
    "WebRTC 直连" -> "WebRTC direct"
    "手机直连，信令服务器配对" -> "Direct connection; pair through the signaling server"
    "开始" -> "Start"
    "扫码配对" -> "Scan to pair"
    "确认配对" -> "Confirm pairing"
    "正在配对" -> "Pairing"
    "已完成配对" -> "Pairing complete"
    "配对失败" -> "Pairing failed"
    "扫描电脑端项目生成的二维码" -> "Scan the QR code generated by the computer project"
    "允许相机权限" -> "Allow camera access"
    "电脑" -> "Computer"
    "服务器" -> "Server"
    "设备指纹" -> "Device fingerprint"
    "二维码有效期" -> "QR code validity"
    "确认并配对" -> "Confirm and pair"
    "正在建立安全连接…" -> "Establishing a secure connection…"
    "已连接到电脑端" -> "Connected to the computer"
    "进入对话" -> "Open conversations"
    "重新扫码" -> "Scan again"
    "未知" -> "Unknown"
    "已过期，请重新生成" -> "Expired; generate a new code"
    "已断开连接，请重试" -> "Connection lost. Please retry"
    "重新配对" -> "Pair again"
    "请确认断开连接" -> "Confirm disconnection"
    "当前连接" -> "Current connection"
    "是否断开？" -> "Disconnect?"
    "电脑文件" -> "Computer file"
    "进行中" -> "In progress"
    "耗时" -> "Duration "
    "已执行" -> "Executed "
    "条" -> " items"
    "命令输出" -> "Command output"
    "命令详情" -> "Command details"
    "命令" -> "Command"
    "输出" -> "Output"
    "复制" -> "Copy"
    "已复制" -> "Copied "
    "图片预览失败" -> "Image preview failed"
    "图片预览" -> "Image preview"
    "文件预览" -> "File preview"
    "关闭文件预览" -> "Close file preview"
    "正在读取文件" -> "Reading file"
    "无法预览" -> "Preview unavailable"
    "（空文件）" -> "(Empty file)"
    "下载中" -> "Downloading"
    "下载" -> "Download"
    "权限确认" -> "Permission required"
    "空闲" -> "Idle"
    "已完成" -> "Completed"
    "已停止" -> "Stopped"
    "失败" -> "Failed"
    "思考" -> "Thinking"
    "执行" -> "Run"
    "搜索" -> "Search"
    "工具" -> "Tool"
    "修改" -> "Edit"
    "说明" -> "Note"
    "计划" -> "Plan"
    "更新计划" -> "Update plan"
    "图片" -> "Image"
    "图像" -> "Image"
    "其他" -> "Other"
    "运行命令" -> "Run command"
    "搜索网页" -> "Search the web"
    "调用工具" -> "Call tool"
    "修改文件" -> "Edit file"
    "执行说明" -> "Execution details"
    "生成图像" -> "Generate image"
    "查看图像" -> "View image"
    "组织回复" -> "Compose response"
    "上下文压缩" -> "Compress context"
    "连接" -> "Connect"
    "恢复" -> "Resume"
    "任务进展" -> "Task progress"
    "等待电脑反馈" -> "Waiting for computer"
    "发送消息" -> "Sending message"
    "正在等待电脑端响应" -> "Waiting for the computer to respond"
    "正在等待电脑端活动更新" -> "Waiting for activity from the computer"
    "电脑端记录暂时无法显示。" -> "The computer record is temporarily unavailable."
    "电脑端暂时没有可显示的消息。" -> "No messages are currently available from the computer."
    "消息已发送，但无法刷新对话" -> "Message sent, but the conversation could not be refreshed"
    "无法发送到电脑" -> "Unable to send to the computer"
    "文件下载失败" -> "File download failed"
    "无法读取附件" -> "Unable to read the attachment"
    "无法创建下载文件" -> "Unable to create the download file"
    "无法写入下载文件" -> "Unable to write the download file"
    "电脑端附件读取失败" -> "Unable to read the attachment from the computer"
    "电脑端请求失败" -> "The computer request failed"
    "电脑端连接失败" -> "Unable to connect to the computer"
    "无法建立电脑端连接" -> "Unable to establish a connection to the computer"
    "RTC 配对失败" -> "RTC pairing failed"
    "二维码信息无效" -> "The QR code is invalid"
    "测试二维码内容无效" -> "The test QR code is invalid"
    "二维码签名验证失败" -> "QR code signature verification failed"
    "配对二维码已过期" -> "The pairing QR code has expired"
    "配对二维码已过期，请让电脑端重新生成" -> "The pairing QR code has expired. Ask the computer to generate a new one"
    "电脑端正在进行，文字可以排队；图片和文件请等当前任务完成后发送" -> "The computer is busy. Text can be queued; wait for the task to finish before sending images or files"
    "电脑端没有接受这条消息，请确认桌面 Codex 已打开该对话" -> "The computer did not accept this message. Make sure Desktop Codex has this conversation open"
    "无法读取电脑端会话" -> "Unable to read conversations from the computer"
    "置顶操作失败" -> "Unable to pin the conversation"
    "归档操作失败" -> "Unable to archive the conversation"
    "无法读取电脑端新建对话选项" -> "Unable to read new conversation options"
    "无法在电脑端新建对话" -> "Unable to create a conversation on the computer"
    "无法读取电脑端确认请求" -> "Unable to read the computer's approval request"
    "无法提交确认结果" -> "Unable to submit the approval result"
    "无法停止电脑端任务" -> "Unable to stop the computer task"
    "通知权限未授予，通知模式已保存，但暂时无法发送通知" -> "Notification permission was not granted. The notification mode was saved, but notifications cannot be sent yet"
    "关闭" -> "Off"
    "关闭推理" -> "Off"
    "极低" -> "Minimal"
    "极简" -> "Minimal"
    "低" -> "Low"
    "高" -> "High"
    "极高" -> "Very high"
    "最高" -> "Max"
    "超高" -> "Ultra"
    "默认推理" -> "Default reasoning"
    "请求批准" -> "Ask for approval"
    "帮我批准" -> "Approve for me"
    "完全访问权限" -> "Full access"
    "未读取" -> "Unavailable"
    "等待确认远程命令" -> "Awaiting confirmation for a remote command"
    "电脑端正在等待你的允许" -> "The computer is waiting for your approval"
    "运行需要确认的远程命令" -> "Run a remote command that needs approval"
    "电脑端正在等待你的允许，确认后会继续执行。" -> "The computer is waiting for your approval and will continue after confirmation."
    "把 Codex 的执行状态整理给我看" -> "Show me the Codex execution status"
    "过程会按事件顺序显示，进行中的任务直接跟随对应行更新。" -> "Events appear in order, and running tasks update beside their row."
    "第一次思考更新：分析任务目标。" -> "First thinking update: analyze the task goal."
    "第二次思考更新：确认执行步骤。" -> "Second thinking update: confirm the execution steps."
    "开始检查电脑端工作区。" -> "Start checking the computer workspace."
    "第一条命令输出。" -> "Output from the first command."
    "第二条命令输出：同类命令更新覆盖上一条。" -> "Output from the second command: the latest update replaces the previous one."
    "搜索结果已返回。" -> "Search results returned."
    "调用远程工具并等待结果。" -> "Call a remote tool and wait for the result."
    "已写入目标文件。" -> "The target file was written."
    "计划已更新。" -> "The plan was updated."
    "已生成图片预览。" -> "Image preview generated."
    "其他操作已完成。" -> "The other operation is complete."
    "整理 CloudX 远程控制项目" -> "Organize the CloudX remote-control project"
    "我正在检查工作区结构，并准备整理远程控制相关模块。" -> "I am checking the workspace structure and preparing the remote-control modules."
    "正在扫描工作区，等待电脑端返回任务进度" -> "Scanning the workspace and waiting for task progress"
    "正在扫描工作区，等待电脑端返回任务进度。" -> "Scanning the workspace and waiting for task progress from the computer."
    "检查远程电脑端工作区" -> "Check the remote computer workspace"
    "任务已完成。执行记录默认收起，点击执行行可以展开查看具体命令。" -> "Task complete. Execution details are collapsed by default; tap a run row to view the command."
    "命令已完成。" -> "The command is complete."
    "文件修改已完成。" -> "The file change is complete."
    "实现扫码配对流程" -> "Implement QR pairing"
    "已完成：配对界面和连接状态展示" -> "Completed: pairing UI and connection status"
    "检查 Windows Connector 状态" -> "Check Windows Connector status"
    "等待下一步指令" -> "Waiting for the next instruction"
    "同步设置页面" -> "Sync the settings page"
    "已完成：外观、通知和设备配对设置" -> "Completed: appearance, notifications, and pairing settings"
    "修复扫码连接超时" -> "Fix QR connection timeout"
    "任务失败：未收到电脑端响应" -> "Failed: no response from the computer"
    "任务失败：未收到电脑端响应。请确认电脑端项目已经启动。" -> "Task failed: no response from the computer. Make sure the computer project is running."
    "未在等待时间内收到电脑端响应。" -> "No response from the computer arrived in time."
    "测试远程任务恢复" -> "Test remote task recovery"
    "已中断：可以从最近消息继续" -> "Interrupted: continue from the latest message"
    "任务已中断，可以从最近消息继续。" -> "Task interrupted. You can continue from the latest message."
    "任务在电脑端停止，当前页面保留最近消息。" -> "The task stopped on the computer; this page keeps the latest messages."
    "整理项目文档" -> "Organize project documentation"
    "已完成项目结构检查" -> "Project structure check completed"
    "已完成：这是电脑端 Codex 返回的对话内容。你可以展开下面的过程行查看任务详情。" -> "Complete: this is the conversation returned by Codex on the computer. Expand the process row below for details."
    "已完成本次任务。这里会显示电脑端返回的过程和任务结果。" -> "This task is complete. The process and result returned by the computer appear here."
    "远程 Codex 对话" -> "Remote Codex conversation"
    "电脑端请求执行一项需要确认的操作。" -> "The computer requested an action that needs your approval."
    "等待电脑端确认请求。" -> "Waiting for an approval request from the computer."
    "已收到这条消息。电脑端返回内容后，会在这里显示完整结果。" -> "Message received. The complete result will appear here when the computer responds."
    "已添加内容" -> "Content added"
    "电脑端附件不可用" -> "The computer attachment is unavailable"
    "当前对话没有可读取的电脑端图片" -> "No computer image is available in this conversation"
    "当前对话没有可读取的电脑端文件" -> "No computer file is available in this conversation"
    "附件" -> "Attachment"
    "内容较长，已截取最近详情。" -> "Long content; showing the latest details."
    "每次最多添加 5 个附件" -> "You can add up to 5 attachments at a time"
    "单个附件不能超过 20 MB" -> "Each attachment must be 20 MB or smaller"
    "允许在工作区内读写" -> "Read and write in the workspace"
    "仅读取，不修改文件" -> "Read only; do not modify files"
    "读取中" -> "Reading"
    "保存图片到本地" -> "Save image locally"
    "正在保存图片" -> "Saving image"
    "分享图片" -> "Share image"
    "图片无法预览" -> "Image cannot be previewed"
    "需要存储权限才能保存图片" -> "Storage permission is required to save the image"
    "无法准备图片" -> "Unable to prepare the image"
    "无法写入图片" -> "Unable to write the image"
    "无法写入本地图片" -> "Unable to write the local image"
    "无法创建本地图片" -> "Unable to create a local image"
    "无法完成本地图片保存" -> "Unable to save the image locally"
    "图片内容为空" -> "Image content is empty"
    "图片超过 100 MB，无法直接预览" -> "Images over 100 MB cannot be previewed directly"
    "图片尺寸无效" -> "Invalid image dimensions"
    "图片无法解码" -> "Unable to decode the image"
    "SVG 图片超过 8 MB" -> "SVG images over 8 MB are not supported"
    "SVG 不允许外部实体" -> "External entities are not allowed in SVG"
    "SVG 图片无法预览" -> "SVG image cannot be previewed"
    "正在打开相机…" -> "Opening camera…"
    "远程 Agent" -> "Remote agent"
    "对应方式" -> "selected connection method"
    "RTC 配对超时，请检查手机网络可访问 HTTPS 信令和 TURN 中继" -> "RTC pairing timed out. Check that the phone can reach HTTPS signaling and the TURN relay"
    "无法解析信令服务器地址，请确认手机网络可访问该 HTTPS 地址" -> "Unable to resolve the signaling server. Check that the phone can reach its HTTPS address"
    "无法连接信令服务器，请确认 HTTPS 服务已启动并可从公网访问" -> "Unable to connect to the signaling server. Check that its HTTPS service is running and publicly reachable"
    "配对协议版本不受支持" -> "The pairing protocol version is not supported"
    "二维码 offerId 无效" -> "The QR code offer ID is invalid"
    "二维码 deviceId 无效" -> "The QR code device ID is invalid"
    "二维码公钥不一致" -> "The QR code public key does not match"
    "二维码 nonce 无效" -> "The QR code nonce is invalid"
    "二维码过期时间不一致" -> "The QR code expiration time does not match"
    "二维码缺少 Connector 签名" -> "The QR code is missing the Connector signature"
    "HTTP 传输缺少 endpoint" -> "The HTTP transport is missing an endpoint"
    "WebRTC 配对缺少 signaling endpoint" -> "WebRTC pairing is missing a signaling endpoint"
    "WebRTC signaling endpoint 无效" -> "The WebRTC signaling endpoint is invalid"
    "正式 WebRTC 信令必须使用 HTTPS" -> "Production WebRTC signaling must use HTTPS"
    "电脑端连接必须使用 HTTPS" -> "The computer connection must use HTTPS"
    "WebRTC 传输缺少 Android Context" -> "The WebRTC transport is missing an Android context"
    "无法解析电脑端地址" -> "Unable to resolve the computer address"
    "WebRTC 尚未接入 HTTP 客户端" -> "WebRTC is not connected to the HTTP client"
    "不接受客户端证书" -> "Client certificates are not accepted"
    "电脑端证书缺失" -> "The computer certificate is missing"
    "电脑端证书指纹与配对信息不一致" -> "The computer certificate fingerprint does not match the pairing information"
    "电脑端证书指纹格式无效" -> "The computer certificate fingerprint format is invalid"
    "无法创建 WebRTC PeerConnection" -> "Unable to create the WebRTC peer connection"
    "无法创建 WebRTC DataChannel" -> "Unable to create the WebRTC data channel"
    "创建 WebRTC PeerConnection" -> "Create WebRTC peer connection"
    "获取桌面端 SDP offer" -> "Get the computer SDP offer"
    "应用桌面端 SDP offer" -> "Apply the computer SDP offer"
    "创建手机端 SDP answer" -> "Create the phone SDP answer"
    "应用手机端 SDP answer" -> "Apply the phone SDP answer"
    "等待手机端 ICE candidates" -> "Wait for phone ICE candidates"
    "提交手机端 SDP answer" -> "Submit the phone SDP answer"
    "提交手机端 ICE candidates" -> "Submit phone ICE candidates"
    "等待桌面端 ICE candidates 和 DataChannel" -> "Wait for computer ICE candidates and the data channel"
    "创建手机端 SDP offer" -> "Create the phone SDP offer"
    "应用手机端 SDP offer" -> "Apply the phone SDP offer"
    "请求桌面端 SDP answer" -> "Request the computer SDP answer"
    "应用桌面端 SDP answer" -> "Apply the computer SDP answer"
    "WebRTC DataChannel 已关闭" -> "The WebRTC data channel is closed"
    "WebRTC DataChannel 发送失败" -> "Unable to send through the WebRTC data channel"
    "WebRTC SDP 为空" -> "The WebRTC SDP is empty"
    "WebRTC SDP 创建失败" -> "Unable to create the WebRTC SDP"
    "WebRTC SDP 设置失败" -> "Unable to set the WebRTC SDP"
    "缺少通知权限，请在系统设置中允许 CloudX 发送通知" -> "Notification permission is missing. Allow CloudX notifications in system settings"
    "系统通知已关闭，请在系统设置中允许 CloudX 发送通知" -> "System notifications are disabled. Allow CloudX notifications in system settings"
    "无法打开导出文件" -> "Unable to open the export file"
    else -> value
}

private val WEBRTC_SIGNALING_FAILURE_PREFIXES = listOf(
    "WebRTC offer 获取失败" to "Unable to get the WebRTC offer",
    "WebRTC answer 登记失败" to "Unable to register the WebRTC answer",
    "WebRTC ICE 登记失败" to "Unable to register WebRTC ICE candidates",
    "WebRTC ICE 获取失败" to "Unable to get WebRTC ICE candidates",
    "WebRTC 信令失败" to "WebRTC signaling failed",
)

internal fun RemoteStrings.localizedPairingError(value: String): String {
    return if (isEnglish) displayText(value) else value
}

internal fun RemoteStrings.localizedCacheMessage(cleared: Boolean): String =
    if (cleared) t("图片预览缓存已清理") else t("缓存清理失败")

internal fun RemoteStrings.localizedDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L
    return if (isEnglish) {
        when {
            hours > 0 -> "%dh %02dm".format(Locale.US, hours, minutes)
            minutes > 0 -> "%dm %02ds".format(Locale.US, minutes, seconds)
            else -> "%ds".format(Locale.US, seconds)
        }
    } else {
        when {
            hours > 0 -> "%d时%02d分".format(Locale.CHINA, hours, minutes)
            minutes > 0 -> "%d分%02d秒".format(Locale.CHINA, minutes, seconds)
            else -> "%d秒".format(Locale.CHINA, seconds)
        }
    }
}
