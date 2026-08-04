package com.denggl2.mason.ui.settings

import com.denggl2.mason.agent.TaskRun
import com.denggl2.mason.agent.stripTaskRunMarkers
import com.denggl2.mason.crashguard.data.CrashRecord
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.LocalModelFileState
import com.denggl2.mason.data.configuredChatModelRef
import com.denggl2.mason.data.configuredImageModelRef
import com.denggl2.mason.data.configuredVisionModelRef
import com.denggl2.mason.data.resolvedConnections
import com.denggl2.mason.data.stripModelParticipationMarkers
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class DiagnosticDeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val supportedAbis: List<String>,
    val locale: String,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val availableStorageBytes: Long,
)

internal data class DiagnosticMessage(
    val role: String,
    val content: String?,
    val timestamp: Long,
    val toolCallName: String? = null,
)

internal data class DiagnosticConversation(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val messages: List<DiagnosticMessage>,
)

internal data class DiagnosticReportInput(
    val generatedAt: Long,
    val appVersion: String,
    val device: DiagnosticDeviceInfo,
    val config: ApiConfig,
    val localModels: List<LocalModelFileState>,
    val conversations: List<DiagnosticConversation>,
    val taskRuns: List<TaskRun>,
    val crashes: List<CrashRecord>,
)

internal fun buildDiagnosticReport(input: DiagnosticReportInput): String {
    val raw = buildString {
        appendLine("Mason 诊断记录")
        appendLine("生成时间: ${formatDiagnosticTime(input.generatedAt)}")
        appendLine("说明: 本文件由用户主动生成，内容已自动脱敏；不包含隐藏思考或工具调用参数。")
        appendLine()

        appendLine("[应用与设备]")
        appendLine("应用版本: ${input.appVersion}")
        appendLine("设备: ${input.device.manufacturer} ${input.device.model}")
        appendLine("Android: ${input.device.androidVersion} (SDK ${input.device.sdk})")
        appendLine("ABI: ${input.device.supportedAbis.joinToString().ifBlank { "unknown" }}")
        appendLine("区域: ${input.device.locale}")
        appendLine("内存: 可用 ${formatDiagnosticBytes(input.device.availableMemoryBytes)} / 总计 ${formatDiagnosticBytes(input.device.totalMemoryBytes)}")
        appendLine("App 可用存储: ${formatDiagnosticBytes(input.device.availableStorageBytes)}")
        appendLine()

        appendLine("[模型配置]")
        appendLine("聊天模型: ${input.config.configuredChatModelRef()?.modelId ?: "未配置"}")
        appendLine("识图模型: ${input.config.configuredVisionModelRef()?.modelId ?: "未配置"}")
        appendLine("生图模型: ${input.config.configuredImageModelRef()?.modelId ?: "未配置"}")
        appendLine("本地模型: ${input.config.localModel.ifBlank { "未配置" }}")
        appendLine("离线兜底: ${input.config.offlineFallbackEnabled}")
        appendLine("动态本地路由: ${input.config.dynamicLocalRoutingEnabled}")
        appendLine("工具调用: ${input.config.toolsEnabled}，需要确认: ${input.config.requireToolConfirmation}")
        input.config.resolvedConnections().forEach { connection ->
            appendLine("- 远端 ${connection.name} (${connection.providerId})")
            appendLine("  地址: ${sanitizeDiagnosticUrl(connection.apiUrl)}")
            appendLine("  模型: ${connection.modelIds.joinToString().ifBlank { "无" }}")
            appendLine("  已验证模型: ${connection.verifiedModelSignatures.keys.joinToString().ifBlank { "无" }}")
        }
        if (input.config.resolvedConnections().isEmpty()) appendLine("- 未配置远端模型")
        input.localModels.forEach { model ->
            appendLine("- 本地 ${model.modelId}: ${model.state}, ${model.fileName ?: "无文件"}, ${formatDiagnosticBytes(model.sizeBytes)}")
        }
        appendLine()

        appendLine("[最近对话]")
        if (input.conversations.isEmpty()) appendLine("无")
        input.conversations.forEach { conversation ->
            appendLine("对话 #${conversation.id}: ${conversation.title.take(DIAGNOSTIC_TITLE_MAX_CHARS)}")
            appendLine("更新时间: ${formatDiagnosticTime(conversation.updatedAt)}")
            conversation.messages.forEach { message ->
                val visibleContent = message.content
                    ?.let(::stripModelParticipationMarkers)
                    ?.let(::stripTaskRunMarkers)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "(空内容)" }
                    .truncateDiagnostic(DIAGNOSTIC_MESSAGE_MAX_CHARS)
                appendLine("- ${formatDiagnosticTime(message.timestamp)} ${message.role}${message.toolCallName?.let { "[$it]" }.orEmpty()}: $visibleContent")
            }
        }
        appendLine()

        appendLine("[最近任务]")
        if (input.taskRuns.isEmpty()) appendLine("无")
        input.taskRuns.forEach { run ->
            appendLine("任务 ${run.id}: ${run.status}, 会话=${run.conversationId ?: "无"}")
            appendLine("目标: ${run.goal.truncateDiagnostic(DIAGNOSTIC_GOAL_MAX_CHARS)}")
            appendLine("更新时间: ${formatDiagnosticTime(run.updatedAt)}")
            appendLine("中断原因: ${run.interruptionReason ?: "无"}")
            run.lastError?.let { appendLine("最后错误: ${it.truncateDiagnostic(DIAGNOSTIC_ERROR_MAX_CHARS)}") }
            if (run.modelContributions.isNotEmpty()) {
                appendLine("参与模型: ${run.modelContributions.joinToString { "${it.modelId}(${it.parts.joinToString("/")})" }}")
            }
            run.steps.forEach { step ->
                appendLine("- ${step.kind}/${step.status}: ${step.title} | ${step.detail.truncateDiagnostic(DIAGNOSTIC_STEP_MAX_CHARS)}")
                step.error?.let { appendLine("  错误: ${it.truncateDiagnostic(DIAGNOSTIC_ERROR_MAX_CHARS)}") }
                step.toolCall?.function?.name?.let { appendLine("  工具: $it（参数未导出）") }
            }
        }
        appendLine()

        appendLine("[崩溃与 ANR]")
        if (input.crashes.isEmpty()) appendLine("无本地记录")
        input.crashes.forEach { crash ->
            appendLine("${formatDiagnosticTime(crash.timestamp)} ${crash.exceptionType} thread=${crash.threadName} launch=${crash.isLaunchCrash}")
            appendLine("消息: ${crash.message.truncateDiagnostic(DIAGNOSTIC_ERROR_MAX_CHARS)}")
            appendLine(crash.stackTrace.truncateDiagnostic(DIAGNOSTIC_STACK_MAX_CHARS))
        }
    }
    val knownSecrets = buildList {
        add(input.config.apiKey)
        input.config.resolvedConnections().mapTo(this) { it.apiKey }
    }
    return redactDiagnosticText(raw, knownSecrets)
}

internal fun redactDiagnosticText(
    text: String,
    knownSecrets: Collection<String> = emptyList(),
): String {
    var redacted = diagnosticUrlPattern.replace(text) { match ->
        sanitizeDiagnosticUrl(match.value)
    }
    knownSecrets
        .asSequence()
        .map(String::trim)
        .filter { it.length >= MIN_SECRET_LENGTH }
        .distinct()
        .sortedByDescending(String::length)
        .forEach { secret -> redacted = redacted.replace(secret, REDACTED_VALUE) }
    redacted = bearerSecretPattern.replace(redacted, "Bearer $REDACTED_VALUE")
    redacted = namedSecretPattern.replace(redacted) { match ->
        "${match.groupValues[1]}${match.groupValues[2]}$REDACTED_VALUE"
    }
    return redacted
}

internal fun sanitizeDiagnosticUrl(value: String): String {
    if (value.isBlank()) return "未配置"
    return runCatching {
        val uri = URI(value)
        val redactedQuery = uri.rawQuery
            ?.split('&')
            ?.joinToString("&") { part -> "${part.substringBefore('=')}=$REDACTED_VALUE" }
        URI(uri.scheme, null, uri.host, uri.port, uri.rawPath, redactedQuery, null).toASCIIString()
    }.getOrElse {
        urlQueryValuePattern.replace(value) { match -> "${match.groupValues[1]}=$REDACTED_VALUE" }
    }
}

private fun String.truncateDiagnostic(maxChars: Int): String =
    if (length <= maxChars) this else take(maxChars) + "\n[内容已截断]"

private fun formatDiagnosticTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(timestamp))

private fun formatDiagnosticBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gib >= 0.1) return "%.2f GiB".format(Locale.US, gib)
    return "%.1f MiB".format(Locale.US, bytes / (1024.0 * 1024.0))
}

private const val DIAGNOSTIC_TITLE_MAX_CHARS = 200
private const val DIAGNOSTIC_MESSAGE_MAX_CHARS = 4_000
private const val DIAGNOSTIC_GOAL_MAX_CHARS = 1_000
private const val DIAGNOSTIC_STEP_MAX_CHARS = 1_000
private const val DIAGNOSTIC_ERROR_MAX_CHARS = 2_000
private const val DIAGNOSTIC_STACK_MAX_CHARS = 12_000
private const val MIN_SECRET_LENGTH = 4
private const val REDACTED_VALUE = "[REDACTED]"
private val diagnosticUrlPattern = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)
private val urlQueryValuePattern = Regex("([?&][^=&#\\s]+)=([^&#\\s]+)")
private val bearerSecretPattern = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{8,}")
private val namedSecretPattern = Regex(
    "(?i)\\b(api[ _-]?key|authorization|access[ _-]?token|refresh[ _-]?token|secret|password)\\b(\\s*[:=]\\s*)[\\\"']?[^\\s,;}\\]\\\"']+",
)
