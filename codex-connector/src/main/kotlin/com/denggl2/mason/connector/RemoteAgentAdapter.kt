package com.denggl2.mason.connector

import com.denggl2.mason.protocol.RemoteAgentKind
import java.nio.file.Path

/**
 * Desktop agent boundary. Transport and pairing expose only these generic
 * conversation contracts; an agent-specific adapter owns its native API.
 */
interface RemoteAgentAdapter : AutoCloseable {
    val kind: RemoteAgentKind
    val displayName: String
    val conversationProvider: RemoteConversationProvider
    val conversationController: RemoteConversationController

    override fun close() = Unit
}

/** Context shared by every desktop agent adapter implementation. */
data class RemoteAgentAdapterContext(
    val store: ConnectorStateStore,
    val workingDirectory: Path,
    val attachmentRoot: Path,
    val codexApi: CodexRemoteControlApi? = null,
)

/** Factory boundary for adding another desktop agent without changing pairing or transport code. */
interface RemoteAgentAdapterFactory {
    val kind: RemoteAgentKind

    fun create(context: RemoteAgentAdapterContext): RemoteAgentAdapter
}

/** Resolves the agent declared in the signed pairing offer. */
class RemoteAgentAdapterRegistry(
    factories: Iterable<RemoteAgentAdapterFactory>,
) {
    private val factoriesByKind = factories.toList().let { factoryList ->
        factoryList.associateBy(RemoteAgentAdapterFactory::kind).also { registered ->
            require(registered.size == factoryList.size) {
            "Each RemoteAgentKind can only have one adapter factory"
            }
        }
    }

    fun create(
        kind: RemoteAgentKind,
        context: RemoteAgentAdapterContext,
    ): RemoteAgentAdapter = factoriesByKind[kind]?.create(context)
        ?: error("No desktop adapter registered for ${kind.name}. Register a RemoteAgentAdapterFactory first.")
}

/** Built-in Codex adapter registration. Other desktop agents plug in beside this factory. */
class CodexRemoteAgentAdapterFactory : RemoteAgentAdapterFactory {
    override val kind: RemoteAgentKind = RemoteAgentKind.MASON_CODEX

    override fun create(context: RemoteAgentAdapterContext): RemoteAgentAdapter {
        return CodexRemoteAgentAdapter(
            api = requireNotNull(context.codexApi) {
                "Codex adapter requires a CodexRemoteControlApi"
            },
            store = context.store,
            attachmentRoot = context.attachmentRoot,
            workingDirectory = context.workingDirectory,
        )
    }
}

/** Codex App Server is the first adapter behind the generic remote protocol. */
class CodexRemoteAgentAdapter(
    api: CodexRemoteControlApi,
    store: ConnectorStateStore,
    messageLimit: Int = 120,
    attachmentRoot: java.nio.file.Path,
    workingDirectory: java.nio.file.Path,
) : RemoteAgentAdapter {
    private val service = RemoteConversationService(
        api = api,
        store = store,
        messageLimit = messageLimit,
        attachmentRoot = attachmentRoot,
        workingDirectory = workingDirectory,
    )

    override val kind: RemoteAgentKind = RemoteAgentKind.MASON_CODEX
    override val displayName: String = "MASON Codex"
    override val conversationProvider: RemoteConversationProvider = service
    override val conversationController: RemoteConversationController = service

    fun record(notification: CodexNotification) {
        service.record(notification)
    }

    fun record(request: CodexServerRequest) {
        service.record(request)
    }
}
