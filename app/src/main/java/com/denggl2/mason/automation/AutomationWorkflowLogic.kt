package com.denggl2.mason.automation

import com.denggl2.mason.data.MasonAutomationAction
import com.denggl2.mason.data.MasonAutomationCondition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AutomationWorkflowLogic {
    private val templateRegex = Regex("\\{\\{([a-zA-Z0-9_.-]+)\\}\\}")
    private val structuredJson = Json { isLenient = true }

    fun interpolate(template: String, values: Map<String, String>): String =
        templateRegex.replace(template) { match -> values[match.groupValues[1]].orEmpty() }

    fun conditionMatches(condition: MasonAutomationCondition?, values: Map<String, String>): Boolean {
        condition ?: return true
        val actual = values[condition.key].orEmpty()
        return when (condition.operator) {
            "exists", "not_empty" -> actual.isNotBlank()
            "empty" -> actual.isBlank()
            "equals" -> actual == condition.value
            "contains" -> actual.contains(condition.value, ignoreCase = true)
            "not_contains" -> !actual.contains(condition.value, ignoreCase = true)
            "not_equals" -> actual != condition.value
            else -> false
        }
    }

    fun normalizedActions(actions: List<MasonAutomationAction>): List<MasonAutomationAction> =
        actions.mapIndexed { index, action ->
            action.copy(
                id = action.id.ifBlank { "step-${index + 1}" },
                title = action.title.ifBlank { defaultTitle(action.type) },
            )
        }

    fun defaultTitle(type: String): String = when (type) {
        AutomationRunner.ACTION_TOOL -> "读取资料"
        AutomationRunner.ACTION_SKILL -> "运行 Skill"
        AutomationRunner.ACTION_MODEL_ARTIFACT -> "AI 生成文件"
        "notification" -> "发送通知"
        "launch_app" -> "打开 App"
        else -> type
    }

    fun requiresModelPlanner(request: String): Boolean = listOf(
        "如果", "否则", "分别", "循环", "失败后", "然后", "再执行", "根据情况",
        "if ", " else", "loop", "on failure", "then ",
    ).any { request.contains(it, ignoreCase = true) }

    fun normalizeStructuredOutput(content: String): String? {
        val candidate = content
            .substringAfter('{', "")
            .substringBeforeLast('}', "")
            .takeIf(String::isNotBlank)
            ?.let { "{$it}" }
            ?: return null
        val root = runCatching { structuredJson.parseToJsonElement(candidate).jsonObject }
            .getOrNull()
            ?: return null
        val normalized = root.toMutableMap()
        normalized["actions"] = (root["actions"] as? JsonArray)
            ?.let { actions ->
                JsonArray(actions.map { action ->
                    val fields = action.jsonObject.toMutableMap()
                    fields["arguments"] = (fields["arguments"] as? JsonObject)
                        ?.let { arguments ->
                            JsonObject(arguments.mapValues { (_, value) ->
                                if (value is JsonPrimitive) JsonPrimitive(value.content) else JsonPrimitive(value.toString())
                            })
                        }
                        ?: JsonObject(emptyMap())
                    fields["continue_on_failure"] = fields["continue_on_failure"]
                        ?.jsonPrimitive
                        ?.let { value -> JsonPrimitive(value.booleanOrNull ?: value.content.equals("true", true)) }
                        ?: JsonPrimitive(false)
                    JsonObject(fields)
                })
            }
            ?: JsonArray(emptyList())
        return JsonObject(normalized).toString()
    }
}
