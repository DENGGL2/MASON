package com.denggl2.mason.ui.chat

import com.denggl2.mason.tool.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDeviceInfoBridgeTest {
    @Test
    fun localDeviceInfoRequestCreatesReadOnlyToolCall() {
        val call = buildLocalDeviceInfoToolCall(
            content = "检测本机手机信息",
            directLocalEnabled = true,
            phoneToolsEnabled = true,
            taskRunId = "task-1",
        )

        requireNotNull(call)
        assertEquals("local-device-info-task-1", call.id)
        assertEquals("get_device_info", call.function.name)
        assertEquals("{}", call.function.arguments)
    }

    @Test
    fun commonDeviceInfoPhrasesAreRecognized() {
        val prompts = listOf(
            "查看手机信息",
            "帮我获取设备参数",
            "这台手机型号是什么",
            "show phone device information",
        )

        prompts.forEach { prompt ->
            assertTrue(
                prompt,
                buildLocalDeviceInfoToolCall(prompt, true, true, "task") != null,
            )
        }
    }

    @Test
    fun bridgeRespectsRouteAndPhoneToolSwitches() {
        assertNull(buildLocalDeviceInfoToolCall("查看手机信息", false, true, "task"))
        assertNull(buildLocalDeviceInfoToolCall("查看手机信息", true, false, "task"))
    }

    @Test
    fun unrelatedLocalPromptsDoNotTriggerDeviceInfo() {
        assertNull(buildLocalDeviceInfoToolCall("介绍一下 Android 系统", true, true, "task"))
        assertNull(buildLocalDeviceInfoToolCall("查看手机电量", true, true, "task"))
        assertNull(buildLocalDeviceInfoToolCall("你好", true, true, "task"))
    }

    @Test
    fun deviceInfoResultUsesStableChineseFormatting() {
        val answer = formatLocalDeviceInfoResult(
            ToolResult.success(
                mapOf(
                    "manufacturer" to "MuMu",
                    "brand" to "Android",
                    "model" to "Emulator",
                    "android_version" to "12",
                    "sdk_level" to "32",
                    "security_patch" to "2023-01-01",
                    "locale" to "zh_CN",
                    "resolution" to "1080x1920",
                    "density_dpi" to "420 DPI",
                ),
            ),
        )

        assertTrue(answer.contains("设备：MuMu Android Emulator"))
        assertTrue(answer.contains("系统：Android 12（SDK 32）"))
        assertTrue(answer.contains("屏幕：1080x1920，420 DPI"))
    }

    @Test
    fun deviceInfoFailureIsVisible() {
        assertEquals(
            "设备信息读取失败：读取异常",
            formatLocalDeviceInfoResult(ToolResult.error("读取异常")),
        )
    }
}
