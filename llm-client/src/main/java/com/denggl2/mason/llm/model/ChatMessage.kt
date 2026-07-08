package com.denggl2.mason.llm.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null,
)

fun ChatMessage.toApiChatMessage(): ApiChatMessage = ApiChatMessage(
    role = role,
    content = content,
    tool_calls = tool_calls,
    tool_call_id = tool_call_id,
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall,
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String,
)
