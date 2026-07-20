package com.denggl2.mason.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryWritePolicyTest {
    @Test
    fun acceptsDurablePreferenceAndRejectsTransientState() {
        val durable = evaluateMemoryWrite("默认语言", "以后默认使用中文")
        val transient = evaluateMemoryWrite("当前任务", "今天先生成一份报告", explicitRequest = true)

        assertTrue(durable.accepted)
        assertFalse(durable.sensitive)
        assertFalse(transient.accepted)
        assertTrue(transient.reason.contains("短期"))
    }

    @Test
    fun infersSensitiveTypesInsteadOfTrustingModelFlag() {
        val address = evaluateMemoryWrite(
            label = "公司地址",
            value = "测试路 1 号",
            requestedSensitive = false,
        )
        val plate = evaluateMemoryWrite(
            label = "车牌",
            value = "TEST123",
            requestedSensitive = false,
        )

        assertEquals(UserMemoryType.ADDRESS, address.type)
        assertTrue(address.sensitive)
        assertEquals(UserMemoryType.LICENSE_PLATE, plate.type)
        assertTrue(plate.sensitive)
    }

    @Test
    fun rejectsOversizedMemoryInsteadOfSilentlyTruncatingAndSaving() {
        val result = evaluateMemoryWrite("x".repeat(41), "value")

        assertFalse(result.accepted)
        assertTrue(result.reason.contains("过长"))
    }
}
