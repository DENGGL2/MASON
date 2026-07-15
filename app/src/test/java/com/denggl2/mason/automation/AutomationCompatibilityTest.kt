package com.denggl2.mason.automation

import com.denggl2.mason.data.MasonAutomationSpec
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AutomationCompatibilityTest {
    @Test
    fun readsLegacySingleActionJson() {
        val spec = Json { ignoreUnknownKeys = true }.decodeFromString<MasonAutomationSpec>(
            """
            {
              "id":"legacy",
              "name":"旧自动化",
              "enabled":true,
              "trigger":{"type":"manual","value":""},
              "actions":[{"type":"notification","arguments":{"text":"ok"}}]
            }
            """.trimIndent(),
        )
        assertEquals("notification", spec.actions.single().type)
        assertEquals("", spec.actions.single().inputKey)
        assertFalse(spec.archived)
    }
}
