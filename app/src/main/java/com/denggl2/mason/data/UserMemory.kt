package com.denggl2.mason.data

import kotlinx.serialization.Serializable

@Serializable
enum class UserMemoryType(val label: String) {
    LICENSE_PLATE("车牌"),
    IDENTITY("身份"),
    ADDRESS("地址"),
    PAYMENT("支付"),
    OTHER("其他"),
}

@Serializable
enum class UserMemoryScope {
    GLOBAL,
    PROJECT,
    CONVERSATION,
}

@Serializable
data class UserMemoryItem(
    val id: String,
    val label: String,
    val value: String,
    val type: UserMemoryType = UserMemoryType.OTHER,
    val sensitive: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = updatedAtMillis,
    val enabled: Boolean = true,
    val autoUse: Boolean = !sensitive,
    val scope: UserMemoryScope = UserMemoryScope.GLOBAL,
    val scopeId: String? = null,
    val keywords: List<String> = emptyList(),
    val lastUsedAtMillis: Long? = null,
)
