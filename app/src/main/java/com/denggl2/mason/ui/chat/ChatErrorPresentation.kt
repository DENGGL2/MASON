package com.denggl2.mason.ui.chat

private val httpStatusPattern = Regex(
    "(?i)(?:api\\s*(?:error|错误)|http\\s*(?:error|错误)?|status(?:\\s+code)?)\\s*[:：]?\\s*(\\d{3})\\b",
)
private val genericStatusPattern = Regex("\\b([45]\\d{2})\\b")
private val providerCodePattern = Regex(
    "(?i)\\b(RateLimitReached|throttling_error|invalid_api_key|model_not_found|invalid_request_error)\\b",
)
private val jsonCodePattern = Regex("(?i)\"code\"\\s*:\\s*\"([A-Za-z][A-Za-z0-9_-]*)\"")
private val providerMessagePattern = Regex("(?i)(?:Your requests|request failed)\\s*[^\"]{0,220}")
private val jsonMessagePattern = Regex("(?i)\"message\"\\s*:\\s*\"([^\"]+)\"")

internal fun summarizeModelErrorV2(message: String): String {
    val normalized = message
        .replace("\\\"", "\"")
        .replace("\\n", " ")
        .replace("\\r", " ")
        .replace("\\t", " ")
    val lower = normalized.lowercase()
    val statusCode = httpStatusPattern.find(normalized)?.groupValues?.getOrNull(1)
        ?: genericStatusPattern.find(normalized)?.groupValues?.getOrNull(1)
    val providerCode = providerCodePattern.find(normalized)?.groupValues?.getOrNull(1)
        ?: jsonCodePattern.find(normalized)?.groupValues?.getOrNull(1)
    val reason = when {
        statusCode == "429" || providerCode.equals("RateLimitReached", ignoreCase = true) ||
            lower.contains("rate limit") || lower.contains("ratelimit") ->
            "服务商限流，请稍后重试"
        statusCode == "408" || lower.contains("timeout") || lower.contains("timed out") || lower.contains("超时") ->
            "请求超时，请检查网络或服务商响应"
        statusCode == "401" || statusCode == "403" || lower.contains("unauthorized") ||
            lower.contains("invalid api key") ->
            "API Key 无效或没有权限"
        statusCode == "404" || lower.contains("not found") || lower.contains("不存在") ->
            "接口地址或模型不存在"
        statusCode == "400" || lower.contains("bad request") || lower.contains("invalid request") ->
            "请求参数不被服务商接受"
        else -> "模型请求失败"
    }
    val detail = providerMessagePattern.find(normalized)?.value
        ?: jsonMessagePattern.findAll(normalized)
            .map { it.groupValues.getOrNull(1).orEmpty() }
            .lastOrNull { it.isNotBlank() && !it.startsWith("litellm.", ignoreCase = true) }
        ?: normalized.substringAfter(':', normalized)
    val cleanedDetail = detail
        .replace(Regex("https?://\\S+"), "[链接已省略]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(220)
        .trimEnd()
    val codeLabel = listOfNotNull(
        statusCode?.let { "HTTP $it" },
        providerCode,
    ).joinToString(" / ")
    return buildList {
        add("请求失败：$reason")
        if (codeLabel.isNotBlank()) add("错误代码：$codeLabel")
        if (cleanedDetail.isNotBlank()) add("错误内容：$cleanedDetail")
    }.joinToString("\n")
}

internal fun isRetryableModelErrorV2(message: String): Boolean {
    val normalized = message.lowercase()
    return Regex("\\b(?:408|425|429|500|502|503|504)\\b").containsMatchIn(normalized) ||
        listOf(
            "timeout",
            "timed out",
            "network",
            "connection reset",
            "connection refused",
            "temporarily unavailable",
            "rate limit",
            "ratelimit",
            "超时",
            "网络",
        ).any(normalized::contains)
}

internal fun stripDiagnosticErrorLines(content: String): String =
    content.lineSequence()
        .filterNot { line ->
            val normalized = line.lowercase()
            normalized.contains("请求失败") ||
                normalized.contains("错误代码") ||
                normalized.contains("错误内容") ||
                normalized.contains("request failed")
        }
        .joinToString("\n")
