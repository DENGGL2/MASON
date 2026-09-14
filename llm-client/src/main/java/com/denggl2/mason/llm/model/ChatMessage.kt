package com.denggl2.mason.llm.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.denggl2.mason.llm.ModelAttachment

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null,
    val name: String? = null,
    val timestamp: Long? = null,
)

@Serializable
data class ApiChatMessage(
    val role: String,
    val content: JsonElement? = null,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null,
    // Legacy OpenAI-compatible relays identify role=function results by name.
    val name: String? = null,
    // Some OpenAI-compatible relays require an id on tool result messages.
    // Keep tool_call_id as well so standard providers continue to match calls.
    val id: String? = null,
)

private fun ChatMessage.apiContent(): JsonElement? = when {
    role == "assistant" && !tool_calls.isNullOrEmpty() -> null
    else -> content?.let(::JsonPrimitive)
}

private fun ChatMessage.apiId(): String? = when (role) {
    "tool" -> tool_call_id
    "assistant" -> tool_calls?.firstOrNull()?.id
    else -> null
}

fun ChatMessage.toApiChatMessage(): ApiChatMessage = ApiChatMessage(
    role = role,
    content = apiContent(),
    tool_calls = tool_calls,
    tool_call_id = tool_call_id,
    id = apiId(),
)

fun ChatMessage.toApiChatMessage(attachments: List<ModelAttachment>): ApiChatMessage = ApiChatMessage(
    role = role,
    content = JsonArray(buildList {
        content?.takeIf(String::isNotBlank)?.let { text ->
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        }
        attachments.forEach { attachment ->
            attachment.inlineText?.takeIf(String::isNotBlank)?.let { text ->
                add(buildJsonObject {
                    put("type", "text")
                    put("text", "附件 ${attachment.name}：\n$text")
                })
            } ?: add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject { put("url", attachment.uri) })
            })
        }
    }),
    tool_calls = tool_calls,
    tool_call_id = tool_call_id,
    id = apiId(),
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ToolCall(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "function",
    val function: FunctionCall,
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String,
)
