package com.denggl2.mason.tool

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolDefinition(
    val type: String,
    val function: FunctionDef,
)

@Serializable
data class FunctionDef(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)
