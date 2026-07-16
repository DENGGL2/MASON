package com.denggl2.mason.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class ToolRegistry @Inject constructor() {
    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun registerAll(toolList: Set<@JvmSuppressWildcards Tool>) {
        toolList.forEach { register(it) }
    }

    @Synchronized
    fun replaceNamespace(prefix: String, toolList: Collection<Tool>) {
        tools.keys.filter { it.startsWith(prefix) }.forEach(tools::remove)
        toolList.forEach(::register)
    }

    fun get(name: String): Tool? = tools[name]

    fun getAll(): List<Tool> = tools.values.toList()

    fun getDefinitions(): List<ToolDefinition> = getAll().map { tool ->
        ToolDefinition(
            type = "function",
            function = FunctionDef(
                name = tool.name,
                description = tool.description,
                parameters = tool.inputSchema ?: buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        tool.parameters.forEach { (key, param) ->
                            putJsonObject(key) {
                                put("type", JsonPrimitive(param.type))
                                put("description", JsonPrimitive(param.description))
                                param.enum?.let { values ->
                                    putJsonArray("enum") {
                                        values.forEach { add(JsonPrimitive(it)) }
                                    }
                                }
                            }
                        }
                    }
                    val required = tool.parameters.filter { it.value.required }.keys
                    if (required.isNotEmpty()) {
                        putJsonArray("required") {
                            required.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                },
            ),
        )
    }
}
