package com.denggl2.mason.connector

import com.denggl2.mason.protocol.RemoteAgentKind
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteConversationEventPage
import com.denggl2.mason.protocol.RemoteConversationPage
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.protocol.RemoteExecutionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Path
import java.nio.file.Files

class RemoteAgentAdapterRegistryTest {
    @Test
    fun registrySelectsFactoryBySignedAgentKind() {
        val adapter = markerAdapter(RemoteAgentKind.CLAUDE_CODE)
        val registry = RemoteAgentAdapterRegistry(
            listOf(markerFactory(RemoteAgentKind.CLAUDE_CODE, adapter)),
        )

        assertEquals(
            adapter,
            registry.create(RemoteAgentKind.CLAUDE_CODE, markerContext()),
        )
    }

    @Test
    fun registryRejectsDuplicateAgentKinds() {
        assertFailsWith<IllegalArgumentException> {
            RemoteAgentAdapterRegistry(
                listOf(
                    markerFactory(RemoteAgentKind.CUSTOM, markerAdapter(RemoteAgentKind.CUSTOM)),
                    markerFactory(RemoteAgentKind.CUSTOM, markerAdapter(RemoteAgentKind.CUSTOM)),
                ),
            )
        }
    }

    @Test
    fun registryDoesNotPretendAnUnregisteredAgentIsSupported() {
        val registry = RemoteAgentAdapterRegistry(emptyList())

        assertFailsWith<IllegalStateException> {
            registry.create(RemoteAgentKind.DEEPSEEK_HARNESS, markerContext())
        }
    }
}

private fun markerFactory(
    kind: RemoteAgentKind,
    adapter: RemoteAgentAdapter,
) = object : RemoteAgentAdapterFactory {
    override val kind: RemoteAgentKind = kind

    override fun create(context: RemoteAgentAdapterContext): RemoteAgentAdapter = adapter
}

private fun markerAdapter(kind: RemoteAgentKind): RemoteAgentAdapter = object : RemoteAgentAdapter {
    override val kind: RemoteAgentKind = kind
    override val displayName: String = kind.name
    override val conversationProvider: RemoteConversationProvider = object : RemoteConversationProvider {
        override suspend fun listConversations(limit: Int, cursor: String?): RemoteConversationPage =
            RemoteConversationPage(emptyList())

        override suspend fun readConversation(threadId: String): RemoteConversationDetail =
            RemoteConversationDetail(RemoteConversationSummary(threadId = threadId, title = threadId), emptyList())

        override suspend fun conversationEvents(afterRevision: Long, waitMillis: Long): RemoteConversationEventPage =
            RemoteConversationEventPage()
    }
    override val conversationController: RemoteConversationController = object : RemoteConversationController {
        override suspend fun interrupt(threadId: String): RemoteExecutionResult =
            RemoteExecutionResult(threadId = threadId, status = com.denggl2.mason.protocol.RemoteExecutionStatus.INTERRUPTED)
    }
}

private fun markerContext() = RemoteAgentAdapterContext(
    store = ConnectorStateStore(Files.createTempFile("mason-agent-adapter", ".json").also(Files::deleteIfExists)),
    workingDirectory = Path.of("."),
    attachmentRoot = Path.of("."),
)
