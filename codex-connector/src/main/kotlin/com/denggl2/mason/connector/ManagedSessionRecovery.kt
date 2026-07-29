package com.denggl2.mason.connector

import com.denggl2.mason.protocol.CodexThreadBinding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class SessionRecoveryStatus {
    RECOVERED,
    MISSING_THREAD_ID,
    THREAD_MISMATCH,
    FAILED,
}

data class SessionRecoveryResult(
    val binding: CodexThreadBinding,
    val status: SessionRecoveryStatus,
    val errorMessage: String? = null,
)

class ManagedSessionRecovery(
    private val store: ConnectorStateStore,
    private val readThread: suspend (threadId: String) -> JsonElement,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun recoverAll(): List<SessionRecoveryResult> = store.managedBindings().map { binding ->
        recover(binding)
    }

    private suspend fun recover(binding: CodexThreadBinding): SessionRecoveryResult = try {
        val response = readThread(binding.codexThreadId)
        val actualThreadId = response.threadId()
        when {
            actualThreadId == null -> SessionRecoveryResult(
                binding,
                SessionRecoveryStatus.MISSING_THREAD_ID,
                "thread/read response did not contain a thread ID",
            )
            actualThreadId != binding.codexThreadId -> SessionRecoveryResult(
                binding,
                SessionRecoveryStatus.THREAD_MISMATCH,
                "Expected ${binding.codexThreadId}, received $actualThreadId",
            )
            else -> {
                store.markRecovered(binding.conversationId, now())
                SessionRecoveryResult(binding, SessionRecoveryStatus.RECOVERED)
            }
        }
    } catch (error: Exception) {
        SessionRecoveryResult(
            binding,
            SessionRecoveryStatus.FAILED,
            error.message ?: error::class.simpleName,
        )
    }
}

private fun JsonElement.threadId(): String? {
    val root = jsonObject
    return root["thread"]
        ?.jsonObject
        ?.get("id")
        ?.jsonPrimitive
        ?.contentOrNull
        ?: root["id"]?.jsonPrimitive?.contentOrNull
}
