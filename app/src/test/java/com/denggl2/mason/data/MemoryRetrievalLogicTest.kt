package com.denggl2.mason.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrievalLogicTest {
    @Test
    fun retrievesOnlyRelevantMemoryWithoutFloodingOnFirstPersonQueries() {
        val items = listOf(
            memory("language", "默认语言", "中文", keywords = listOf("语言", "默认")),
            memory("theme", "界面主题", "深色", keywords = listOf("主题")),
        )

        val result = rankRelevantMemories(items, "我希望默认使用什么语言")

        assertEquals(listOf("language"), result.map(UserMemoryItem::id))
    }

    @Test
    fun sensitiveMemoryRequiresExplicitIntentAndMatchingType() {
        val identity = memory(
            id = "identity",
            label = "姓名",
            value = "测试用户",
            type = UserMemoryType.IDENTITY,
            sensitive = true,
        )

        assertTrue(rankRelevantMemories(listOf(identity), "介绍一下功能").isEmpty())
        assertTrue(rankRelevantMemories(listOf(identity), "我叫什么", allowSensitive = false).isEmpty())
        assertEquals(
            listOf("identity"),
            rankRelevantMemories(listOf(identity), "我叫什么", allowSensitive = true)
                .map(UserMemoryItem::id),
        )
        assertTrue(allowsSensitiveMemory("我叫什么"))
        assertFalse(allowsSensitiveMemory("介绍一下我能做什么"))
    }

    @Test
    fun conversationMemoryDoesNotLeakAcrossScopes() {
        val global = memory("global", "默认语言", "中文")
        val first = memory("first", "项目语言", "Kotlin", scopeId = "conversation-1")
        val second = memory("second", "项目语言", "Rust", scopeId = "conversation-2")

        val result = rankRelevantMemories(
            listOf(global, first, second),
            query = "项目语言是什么",
            conversationScopeId = "conversation-1",
        )

        assertEquals(listOf("first"), result.map(UserMemoryItem::id))
    }

    @Test
    fun activeProjectMemoryIsAvailableWithoutLeakingOtherProjects() {
        val mason = memory(
            id = "mason",
            label = "技术栈",
            value = "Kotlin",
            scope = UserMemoryScope.PROJECT,
            scopeId = "mason",
        )
        val alpha = memory(
            id = "alpha",
            label = "技术栈",
            value = "Rust",
            scope = UserMemoryScope.PROJECT,
            scopeId = "alpha",
        )

        val result = rankRelevantMemories(
            items = listOf(mason, alpha),
            query = "继续",
            projectScopeId = "mason",
        )

        assertEquals(listOf("mason"), result.map(UserMemoryItem::id))
    }

    private fun memory(
        id: String,
        label: String,
        value: String,
        type: UserMemoryType = UserMemoryType.OTHER,
        sensitive: Boolean = false,
        keywords: List<String> = emptyList(),
        scopeId: String? = null,
        scope: UserMemoryScope = if (scopeId == null) UserMemoryScope.GLOBAL else UserMemoryScope.CONVERSATION,
    ) = UserMemoryItem(
        id = id,
        label = label,
        value = value,
        type = type,
        sensitive = sensitive,
        autoUse = !sensitive,
        keywords = keywords,
        scope = scope,
        scopeId = scopeId,
    )
}
