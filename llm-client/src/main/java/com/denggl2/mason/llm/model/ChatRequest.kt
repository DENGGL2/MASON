package com.denggl2.mason.llm.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiChatMessage>,
    val stream: Boolean = true,
    val tools: List<com.denggl2.mason.tool.ToolDefinition>? = null,
    val tool_choice: String? = null,
    val stream_options: StreamOptions? = null,
    val max_tokens: Int? = null,
)

@Serializable
data class StreamOptions(
    val include_usage: Boolean = true,
)
