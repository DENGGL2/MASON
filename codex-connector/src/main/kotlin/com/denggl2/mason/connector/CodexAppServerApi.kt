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

class CodexAppServerApi(
    private val client: CodexAppServerClient,
) : CodexThreadHistoryApi {
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

    suspend fun startThread(
        cwd: String,
        approvalPolicy: String = "on-request",
        sandbox: String = "workspace-write",
    ): JsonElement = client.request(
        "thread/start",
        buildJsonObject {
            put("cwd", cwd)
            put("approvalPolicy", approvalPolicy)
            put("sandbox", sandbox)
        },
    )

    suspend fun resumeThread(
        threadId: String,
        approvalPolicy: String = "on-request",
        sandbox: String = "workspace-write",
    ): JsonElement = client.request(
        "thread/resume",
        buildJsonObject {
            put("threadId", threadId)
            put("approvalPolicy", approvalPolicy)
            put("sandbox", sandbox)
        },
    )

    suspend fun startTextTurn(threadId: String, text: String): JsonElement = client.request(
        "turn/start",
        buildJsonObject {
            put("threadId", threadId)
            put("input", JsonArray(listOf(buildJsonObject {
                put("type", "text")
                put("text", text)
            })))
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

    suspend fun interruptTurn(threadId: String, turnId: String): JsonElement = client.request(
        "turn/interrupt",
        buildJsonObject {
            put("threadId", threadId)
            put("turnId", turnId)
        },
    )
}

private fun kotlinxString(value: String): JsonElement = kotlinx.serialization.json.JsonPrimitive(value)
