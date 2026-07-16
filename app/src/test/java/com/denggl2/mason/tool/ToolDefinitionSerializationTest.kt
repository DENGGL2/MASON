package com.denggl2.mason.tool

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDefinitionSerializationTest {
    @Test
    fun includesRequiredFunctionTypeInRequestJson() {
        val definition = ToolDefinition(
            type = "function",
            function = FunctionDef(
                name = "probe",
                description = "probe",
                parameters = buildJsonObject {},
            ),
        )

        val encoded = Json.encodeToString(definition)

        assertTrue(encoded.contains("\"type\":\"function\""))
    }
}
