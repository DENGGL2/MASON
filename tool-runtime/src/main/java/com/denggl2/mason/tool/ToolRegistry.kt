package com.denggl2.mason.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor() {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun registerAll(toolList: Set<@JvmSuppressWildcards Tool>) {
        toolList.forEach { register(it) }
    }

    fun get(name: String): Tool? = tools[name]

    fun getAll(): List<Tool> = tools.values.toList()

    fun getDefinitions(): List<ToolDefinition> = getAll().map { tool ->
        ToolDefinition(
            function = FunctionDef(
                name = tool.name,
                description = tool.description,
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        tool.parameters.forEach { (key, param) ->
                            putJsonObject(key) {
                                put("type", param.type)
                                put("description", param.description)
                                param.enum?.let { values ->
                                    putJsonArray("enum") {
                                        values.forEach { add(it) }
                                    }
                                }
                            }
                        }
                    }
                    val required = tool.parameters.filter { it.value.required }.keys
                    if (required.isNotEmpty()) {
                        putJsonArray("required") {
                            required.forEach { add(it) }
                        }
                    }
                },
            ),
        )
    }
}
