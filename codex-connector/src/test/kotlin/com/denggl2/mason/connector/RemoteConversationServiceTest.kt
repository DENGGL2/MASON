package com.denggl2.mason.connector

import com.denggl2.mason.protocol.CodexOwnership
import com.denggl2.mason.protocol.CodexThreadBinding
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.RemoteConversationRole
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement

class RemoteConversationServiceTest {
    @Test
    fun projectsPagedThreadListWithoutLeakingRawCodexJson() = withStore { store ->
        store.register(
            CodexThreadBinding(
                conversationId = "conversation-1",
                deviceId = store.deviceId,
                codexThreadId = "thread-1",
                ownership = CodexOwnership.MASON_MANAGED,
                protocolVersion = "1",
            ),
        )
        val api = FakeThreadHistoryApi(
            listResponse = json(
                """{
                    "data": [
                        {"id":"thread-1","name":"计划","preview":"第一条\n预览","updatedAt":100,"cwd":"D:/Work/One"},
                        {"id":"thread-2","preview":"第二条会话","updated_at":"2026-07-30T00:00:00Z"}
                    ],
                    "nextCursor":"cursor-2"
                }""",
            ),
            readResponse = json("{}"),
        )

        val page = runBlocking {
            RemoteConversationService(api, store).listConversations(limit = 3, cursor = "cursor-1")
        }

        assertEquals(3, api.lastLimit)
        assertEquals("cursor-1", api.lastCursor)
        assertEquals("cursor-2", page.nextCursor)
        assertEquals("计划", page.conversations[0].title)
        assertEquals("第一条 预览", page.conversations[0].preview)
        assertEquals(100_000, page.conversations[0].updatedAt)
        assertEquals(CodexOwnership.MASON_MANAGED, page.conversations[0].ownership)
        assertEquals(CodexOwnership.EXTERNAL_HISTORY_ONLY, page.conversations[1].ownership)
        assertTrue(page.conversations[1].updatedAt > 0)
    }

    @Test
    fun readsOnlyRecentUserAndAssistantMessagesAcrossKnownShapes() = withStore { store ->
        val api = FakeThreadHistoryApi(
            listResponse = json("{}"),
            readResponse = json(
                """{
                    "thread": {
                        "id":"thread-1",
                        "preview":"测试会话",
                        "turns":[
                            {"items":[
                                {"type":"userMessage","content":[{"type":"text","text":"问题一"}]},
                                {"type":"reasoning","summary":["hidden"]},
                                {"type":"agentMessage","text":"回答一"},
                                {"role":"user","content":"问题二"}
                            ]}
                        ]
                    }
                }""",
            ),
        )

        val detail = runBlocking {
            RemoteConversationService(api, store, messageLimit = 2).readConversation("thread-1")
        }

        assertTrue(detail.hasEarlierMessages)
        assertEquals(listOf(RemoteConversationRole.ASSISTANT, RemoteConversationRole.USER), detail.messages.map { it.role })
        assertEquals(listOf("回答一", "问题二"), detail.messages.map { it.text })
        assertFalse(detail.messages.any { it.text.contains("hidden") })
    }
}

private class FakeThreadHistoryApi(
    private val listResponse: JsonElement,
    private val readResponse: JsonElement,
) : CodexThreadHistoryApi {
    var lastLimit: Int? = null
    var lastCursor: String? = null

    override suspend fun listThreads(limit: Int, cursor: String?): JsonElement {
        lastLimit = limit
        lastCursor = cursor
        return listResponse
    }

    override suspend fun readThread(threadId: String, includeTurns: Boolean): JsonElement = readResponse
}

private fun json(value: String): JsonElement = MasonProtocolJson.format.parseToJsonElement(value)

private fun withStore(block: (ConnectorStateStore) -> Unit) {
    val path = Files.createTempFile("mason-remote-conversations", ".json")
    Files.deleteIfExists(path)
    try {
        block(
            ConnectorStateStore(
                statePath = path,
                newOwnerId = { "owner-1" },
                newDeviceId = { "connector-1" },
            ),
        )
    } finally {
        Files.deleteIfExists(path)
    }
}
