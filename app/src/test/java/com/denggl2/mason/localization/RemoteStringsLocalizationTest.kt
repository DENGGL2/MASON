package com.denggl2.mason.localization

import com.denggl2.masonremote.ui.resolveRemoteStrings
import com.denggl2.masonremote.ui.settings.RemoteLanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteStringsLocalizationTest {
    private val strings = resolveRemoteStrings(
        preference = RemoteLanguagePreference.ENGLISH,
        systemLanguage = "zh",
    )

    @Test
    fun `english language name stays English`() {
        assertEquals("English", strings.t("英文"))
    }

    @Test
    fun `dynamic application text is fully translated`() {
        assertEquals("3 installed", strings.displayText("已安装 3 个"))
        assertEquals("Send a message to Desktop", strings.displayText("向 Desktop 发送消息"))
        assertEquals(
            "This will delete 2 files and cannot be undone.",
            strings.displayText("将删除 2 个文件，删除后不能恢复。"),
        )
        assertEquals("Unable to connect to the computer", strings.displayText("电脑端连接失败"))
    }

    @Test
    fun `integration catalog text is fully translated`() {
        assertEquals("WeChat", strings.displayText("微信"))
        assertEquals("Send message · Voice calls · Video calls", strings.displayText("发送消息 · 语音通话 · 视频通话"))
        assertEquals(
            "View repositories, issues, and pull requests, and handle development collaboration tasks",
            strings.displayText("查看仓库、Issue 和 Pull Request，并执行研发协作任务"),
        )
        assertEquals("Connect WeChat", strings.displayText("连接微信"))
        assertEquals(
            "Not installed; official integration is not yet available",
            strings.displayText("未安装；官方接入尚未开放"),
        )
        assertEquals("Installed; waiting for official integration", strings.displayText("已安装，等待官方开放接入"))
        assertEquals(
            "Waiting for official access · Send message, Voice calls, Video calls",
            strings.displayText("等待官方接入 · 发送消息、语音通话、视频通话"),
        )
        assertEquals(
            "No tool service is connected. Supported uses: Search, File, Maps, GitHub, Office systems.",
            strings.displayText("当前未连接工具服务。支持的用途：搜索、文件、地图、GitHub、办公系统。"),
        )
    }

    @Test
    fun `integration runtime status text is translated`() {
        assertEquals("Discovering", strings.displayText("正在发现"))
        assertEquals("MCP 3 tools", strings.displayText("MCP 3 个工具"))
        assertEquals("https 2 capabilities", strings.displayText("https 2 项能力"))
        assertEquals(
            "GitHub connection configuration saved",
            strings.displayText("GitHub 连接配置已保存"),
        )
        assertEquals(
            "GitHub is configured, but the connection is not ready",
            strings.displayText("GitHub 已配置，但连接尚未就绪"),
        )
    }

    @Test
    fun `integration protocol and oauth failures are translated`() {
        assertEquals(
            "Only complete HTTP or HTTPS URLs are supported",
            strings.displayText("仅支持完整的 HTTP 或 HTTPS 地址"),
        )
        assertEquals(
            "MCP tools/call returned no content",
            strings.displayText("MCP tools/call 没有返回内容"),
        )
        assertEquals(
            "OAuth token exchange failed: HTTP 400 invalid_grant",
            strings.displayText("OAuth 换取 Token 失败：HTTP 400 invalid_grant"),
        )
        assertEquals(
            "Failed to read OAuth metadata: HTTP 503",
            strings.displayText("读取 OAuth 元数据失败：HTTP 503"),
        )
        assertEquals(
            "The A2A response is missing result",
            strings.displayText("A2A 响应缺少 result"),
        )
    }

    @Test
    fun `integration task and approval wrappers are translated`() {
        assertEquals(
            "Handed off to Report Agent",
            strings.displayText("已交给 Report Agent 处理"),
        )
        assertEquals(
            "Delegated to Report Agent",
            strings.displayText("委派给 Report Agent"),
        )
        assertEquals(
            "Hand the current task to Report Agent; available skills: Research, Drafting",
            strings.displayText("把当前任务交给 Report Agent 处理，可使用：Research、Drafting"),
        )
        assertEquals(
            "Delegate the task to Report Agent: Handles reports. Capabilities: Research, Drafting",
            strings.displayText("将任务委派给 Report Agent: Handles reports。能力：Research、Drafting"),
        )
        assertEquals(
            "Allow the Create issue tool from GitHub to handle this task",
            strings.displayText("允许 GitHub 的 Create issue 工具处理本轮任务"),
        )
    }
}
