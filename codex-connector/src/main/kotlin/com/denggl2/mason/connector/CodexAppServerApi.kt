package com.denggl2.mason.connector

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface CodexThreadHistoryApi {
    suspend fun listThreads(limit: Int = 50, cursor: String? = null): JsonElement
    suspend fun readThread(threadId: String, includeTurns: Boolean = true): JsonElement
}

interface CodexRemoteControlApi : CodexThreadHistoryApi {
    suspend fun resumeThread(threadId: String): JsonElement =
        throw UnsupportedOperationException("Codex thread resume is unavailable")

    suspend fun startThread(
        cwd: String? = null,
        model: String? = null,
        permissions: String? = null,
    ): JsonElement = throw UnsupportedOperationException("Codex thread start is unavailable")

    suspend fun listModels(): JsonElement = JsonObject(emptyMap())

    suspend fun listSkills(cwds: List<String>): JsonElement = JsonObject(emptyMap())

    suspend fun listPermissionProfiles(cwd: String?): JsonElement = JsonObject(emptyMap())

    suspend fun readConfig(cwd: String?): JsonElement = JsonObject(emptyMap())

    suspend fun updateThreadMetadata(threadId: String, isPinned: Boolean): JsonElement =
        throw UnsupportedOperationException("Codex thread metadata updates are unavailable")

    suspend fun archiveThread(threadId: String): JsonElement =
        throw UnsupportedOperationException("Codex thread archive is unavailable")

    suspend fun startTextTurn(threadId: String, text: String): JsonElement =
        throw UnsupportedOperationException("Codex turn start is unavailable")

    suspend fun startTurn(
        threadId: String,
        input: JsonArray,
        model: String? = null,
        effort: String? = null,
        permissions: String? = null,
    ): JsonElement {
        val textInput = input.singleOrNull()?.let(JsonElement::asTextInput)
        if (model == null && effort == null && permissions == null && textInput != null) {
            return startTextTurn(threadId, textInput)
        }
        throw UnsupportedOperationException("Enhanced Codex turn start is unavailable")
    }

    suspend fun interruptTurn(threadId: String, turnId: String): JsonElement
}

class CodexAppServerApi(
    private val client: CodexAppServerClient,
) : CodexRemoteControlApi {
    override suspend fun listThreads(limit: Int, cursor: String?): JsonElement = client.request(
        "thread/list",
        buildJsonObject {
            put("limit", limit)
            put("sortKey", "updated_at")
            put("sourceKinds", JsonArray(listOf("appServer", "cli", "vscode").map(::kotlinxString)))
            cursor?.let { put("cursor", it) }
        },
    )

    override suspend fun readThread(threadId: String, includeTurns: Boolean): JsonElement = client.request(
        "thread/read",
        buildJsonObject {
            put("threadId", threadId)
            put("includeTurns", includeTurns)
        },
    )

    override suspend fun startThread(
        cwd: String?,
        model: String?,
        permissions: String?,
    ): JsonElement = client.request(
        "thread/start",
        buildJsonObject {
            cwd?.let { put("cwd", it) }
            model?.let { put("model", it) }
            permissions?.let { put("permissions", it) }
        },
    )

    override suspend fun resumeThread(threadId: String): JsonElement = client.request(
        "thread/resume",
        buildJsonObject {
            put("threadId", threadId)
        },
    )

    override suspend fun listModels(): JsonElement = client.request(
        "model/list",
        buildJsonObject {
            put("limit", 100)
            put("includeHidden", false)
        },
    )

    override suspend fun listSkills(cwds: List<String>): JsonElement = client.request(
        "skills/list",
        buildJsonObject {
            put("cwds", JsonArray(cwds.map(::kotlinxString)))
            put("forceReload", false)
        },
    )

    override suspend fun listPermissionProfiles(cwd: String?): JsonElement = client.request(
        "permissionProfile/list",
        buildJsonObject {
            put("limit", 100)
            cwd?.let { put("cwd", it) }
        },
    )

    override suspend fun readConfig(cwd: String?): JsonElement = client.request(
        "config/read",
        buildJsonObject {
            cwd?.let { put("cwd", it) }
            put("includeLayers", false)
        },
    )

    override suspend fun updateThreadMetadata(threadId: String, isPinned: Boolean): JsonElement = client.request(
        "thread/metadata/update",
        buildJsonObject {
            put("threadId", threadId)
            put("isPinned", isPinned)
        },
    )

    override suspend fun archiveThread(threadId: String): JsonElement = client.request(
        "thread/archive",
        buildJsonObject {
            put("threadId", threadId)
        },
    )

    override suspend fun startTurn(
        threadId: String,
        input: JsonArray,
        model: String?,
        effort: String?,
        permissions: String?,
    ): JsonElement = client.request(
        "turn/start",
        buildJsonObject {
            put("threadId", threadId)
            put("input", input)
            model?.let { put("model", it) }
            effort?.let { put("effort", it) }
            permissions?.let { put("permissions", it) }
        },
    )

    suspend fun steerTurn(threadId: String, text: String): JsonElement = client.request(
        "turn/steer",
        buildJsonObject {
            put("threadId", threadId)
            put("input", JsonArray(listOf(buildJsonObject {
                put("type", "text")
                put("text", text)
            })))
        },
    )

    override suspend fun interruptTurn(threadId: String, turnId: String): JsonElement = client.request(
        "turn/interrupt",
        buildJsonObject {
            put("threadId", threadId)
            put("turnId", turnId)
        },
    )
}

private fun kotlinxString(value: String): JsonElement = kotlinx.serialization.json.JsonPrimitive(value)

private fun JsonElement.asTextInput(): String? = (this as? JsonObject)
    ?.takeIf { it["type"]?.toString() == "\"text\"" }
    ?.get("text")
    ?.let { value -> (value as? kotlinx.serialization.json.JsonPrimitive)?.content }
