package com.denggl2.mason.data

data class MemoryWriteEvaluation(
    val accepted: Boolean,
    val label: String,
    val value: String,
    val type: UserMemoryType,
    val sensitive: Boolean,
    val reason: String,
)

internal fun evaluateMemoryWrite(
    label: String,
    value: String,
    requestedType: UserMemoryType? = null,
    requestedSensitive: Boolean = false,
    explicitRequest: Boolean = false,
): MemoryWriteEvaluation {
    val cleanLabel = label.trim().take(40)
    val cleanValue = value.trim().take(500)
    val inferredType = requestedType ?: inferMemoryType(cleanLabel, cleanValue)
    val sensitive = requestedSensitive || inferredType in sensitiveMemoryTypes
    val combined = "$cleanLabel $cleanValue"
    val transient = transientMemoryTerms.any(combined::contains) &&
        durableMemoryTerms.none(combined::contains)
    val accepted = cleanLabel.isNotBlank() && cleanValue.isNotBlank() &&
        label.trim().length <= 40 && value.trim().length <= 500 &&
        (!transient || explicitRequest && durableMemoryTerms.any(combined::contains))
    val reason = when {
        cleanLabel.isBlank() || cleanValue.isBlank() -> "记忆标签和值不能为空"
        label.trim().length > 40 || value.trim().length > 500 -> "记忆内容过长"
        transient -> "这条信息看起来只对当前或短期任务有效，不写入长期记忆"
        sensitive -> "这是敏感记忆，写入前需要用户确认"
        else -> "这是可复用的长期偏好或事实"
    }
    return MemoryWriteEvaluation(
        accepted = accepted,
        label = cleanLabel,
        value = cleanValue,
        type = inferredType,
        sensitive = sensitive,
        reason = reason,
    )
}

internal fun inferMemoryType(label: String, value: String): UserMemoryType {
    val text = "$label $value"
    return when {
        listOf("车牌", "车号").any(text::contains) -> UserMemoryType.LICENSE_PLATE
        listOf("身份", "姓名", "名字", "称呼", "身份证").any(text::contains) -> UserMemoryType.IDENTITY
        listOf("地址", "住址", "小区", "公司位置", "家里").any(text::contains) -> UserMemoryType.ADDRESS
        listOf("支付", "账号", "收款", "银行卡", "支付宝").any(text::contains) -> UserMemoryType.PAYMENT
        else -> UserMemoryType.OTHER
    }
}

internal val sensitiveMemoryTypes = setOf(
    UserMemoryType.LICENSE_PLATE,
    UserMemoryType.IDENTITY,
    UserMemoryType.ADDRESS,
    UserMemoryType.PAYMENT,
)

private val transientMemoryTerms = listOf("这次", "当前", "现在", "今天", "明天", "刚才", "临时", "本次")
private val durableMemoryTerms = listOf("以后", "长期", "总是", "默认", "习惯", "偏好", "一直")
