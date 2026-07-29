package com.denggl2.mason.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CodexWireMessageRouterTest {
    @Test
    fun methodAndIdIsServerRequestEvenWhenIdMatchesClientRequest() {
        val message = buildJsonObject {
            put("id", 1)
            put("method", "item/commandExecution/requestApproval")
            put("params", buildJsonObject { put("command", "Get-Date") })
        }

        val routed = assertIs<CodexWireMessage.ServerRequest>(CodexWireMessageRouter.classify(message))

        assertEquals("item/commandExecution/requestApproval", routed.method)
        assertEquals("Get-Date", routed.params["command"]?.toString()?.trim('"'))
    }

    @Test
    fun idWithoutMethodIsResponse() {
        val message = buildJsonObject {
            put("id", 1)
            put("result", buildJsonObject { put("ok", true) })
        }

        assertIs<CodexWireMessage.Response>(CodexWireMessageRouter.classify(message))
    }
}
