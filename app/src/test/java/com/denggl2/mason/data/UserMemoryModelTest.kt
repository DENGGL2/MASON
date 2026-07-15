package com.denggl2.mason.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryModelTest {
    @Test
    fun oldMemoryJsonUsesSafeDefaults() {
        val item = Json { ignoreUnknownKeys = true }.decodeFromString<UserMemoryItem>(
            """{"id":"1","label":"公司地址","value":"测试路 1 号","sensitive":true}""",
        )
        assertEquals(UserMemoryScope.GLOBAL, item.scope)
        assertTrue(item.enabled)
        assertFalse(item.autoUse)
    }

    @Test
    fun projectMemoryRoundTripsScopeAndKeywords() {
        val json = Json { encodeDefaults = true }
        val item = UserMemoryItem(
            id = "2",
            label = "项目语言",
            value = "Kotlin",
            sensitive = false,
            scope = UserMemoryScope.PROJECT,
            scopeId = "mason",
            keywords = listOf("语言", "技术栈"),
        )
        assertEquals(item, json.decodeFromString<UserMemoryItem>(json.encodeToString(item)))
    }
}
