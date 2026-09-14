package com.denggl2.mason.model

import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ApiModelCapabilities
import com.denggl2.mason.data.ModelReference
import com.denggl2.mason.data.configuredChatModelRef
import com.denggl2.mason.data.selectChatModel
import com.denggl2.mason.llm.ModelAttachment
import com.denggl2.mason.llm.ModelModality
import com.denggl2.mason.llm.model.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelRoutingLogicTest {
    @Test
    fun dynamicRoutingUsesOnlyConfiguredModelsAndPrefersCurrentForStandardTasks() {
        val config = routingConfig(dynamic = true)

        val selected = requireNotNull(
            selectConfiguredRemoteChatModel(
                config = config,
                userText = "帮我整理这段内容并给出简短建议",
                toolsRequested = false,
            ),
        )

        assertEquals(ModelReference("deepseek", "deepseek-v4-flash"), selected.reference)
    }

    @Test
    fun dynamicRoutingUsesLightModelForSimpleTaskAndStrongModelForComplexTask() {
        val config = routingConfig(dynamic = true)

        val simple = requireNotNull(
            selectConfiguredRemoteChatModel(config, "翻译：早上好", toolsRequested = false),
        )
        val complex = requireNotNull(
            selectConfiguredRemoteChatModel(
                config,
                "请深入分析这个系统并给出完整架构设计",
                toolsRequested = false,
            ),
        )

        assertEquals("deepseek-v4-flash", simple.reference.modelId)
        assertEquals("deepseek-v4-pro", complex.reference.modelId)
    }

    @Test
    fun toolIntentSelectsConfiguredToolCapableModel() {
        val config = routingConfig(dynamic = true).selectChatModel(
            ModelReference("mimo", "mimo-v2.5"),
        )

        val selected = requireNotNull(
            selectConfiguredRemoteChatModel(
                config,
                "请搜索最新消息并发送给我",
                toolsRequested = true,
            ),
        )

        assertEquals(ModelReference("deepseek", "deepseek-v4-flash"), selected.reference)
    }

    @Test
    fun toolsAreNeverSentToUnsupportedOrNonTextModels() {
        assertFalse(
            shouldEnableRemoteTools(
                requested = true,
                phoneToolsEnabled = true,
                modelSupportsTools = false,
                modality = ModelModality.Text,
                useLocal = false,
            ),
        )
        assertFalse(
            shouldEnableRemoteTools(
                requested = true,
                phoneToolsEnabled = true,
                modelSupportsTools = true,
                modality = ModelModality.Vision,
                useLocal = false,
            ),
        )
        assertTrue(
            shouldEnableRemoteTools(
                requested = true,
                phoneToolsEnabled = true,
                modelSupportsTools = true,
                modality = ModelModality.Text,
                useLocal = false,
            ),
        )
    }

    @Test
    fun toolIntentRecognizesDeviceInspectionButNotOrdinaryChat() {
        assertTrue(isLikelyToolRequest("查看手机配置"))
        assertTrue(isLikelyToolRequest("检查本机设备信息"))
        assertFalse(isLikelyToolRequest("hello"))
        assertFalse(isLikelyToolRequest("帮我写一段关于手机的介绍"))
    }

    @Test
    fun oldToolRoundsDoNotEnableToolsForANewUserTurn() {
        val oldRound = listOf(
            ChatMessage(role = "user", content = "查看手机配置"),
            ChatMessage(role = "assistant", tool_calls = listOf(
                com.denggl2.mason.llm.model.ToolCall(
                    id = "call_1",
                    function = com.denggl2.mason.llm.model.FunctionCall("get_device_info", "{}"),
                ),
            )),
            ChatMessage(role = "tool", content = "model: test"),
        )
        assertTrue(oldRound.hasToolRoundAfterLatestUser())
        assertFalse((oldRound + ChatMessage(role = "user", content = "hello"))
            .hasToolRoundAfterLatestUser())
    }

    @Test
    fun disabledDynamicRoutingAlwaysKeepsTheCurrentModel() {
        val config = routingConfig(dynamic = false)

        val selected = requireNotNull(
            selectConfiguredRemoteChatModel(
                config,
                "请深入分析并给出完整架构设计",
                toolsRequested = true,
            ),
        )

        assertEquals(config.configuredChatModelRef(), selected.reference)
    }

    @Test
    fun timeoutMessageUsesTheConfiguredTimeout() {
        assertEquals("模型响应超过 15 秒，已停止", remoteTimeoutMessage(15_000L))
    }

    @Test
    fun imageRequestDoesNotFallBackToTextWhenImageModelIsMissing() {
        assertEquals(
            ModelModality.ImageGeneration,
            detectModelModality("请生成图片：一座未来城市", emptyList(), emptyList()),
        )
    }

    @Test
    fun imageIntentRecognizesCommonNaturalLanguageRequests() {
        assertEquals(
            ModelModality.ImageGeneration,
            detectModelModality("画一个红苹果", emptyList(), emptyList()),
        )
        assertEquals(
            ModelModality.ImageGeneration,
            detectModelModality("生成一幅白底红苹果", emptyList(), emptyList()),
        )
        assertEquals(
            ModelModality.ImageGeneration,
            detectModelModality("create a picture of a red apple", emptyList(), emptyList()),
        )
        assertEquals(
            ModelModality.ImageGeneration,
            detectModelModality("draw an icon for a weather app", emptyList(), emptyList()),
        )
        assertEquals(
            ModelModality.Text,
            detectModelModality("generate a report about the image pipeline", emptyList(), emptyList()),
        )
    }

    @Test
    fun renderedPdfPagesUseVisionRouting() {
        assertEquals(
            ModelModality.Vision,
            detectModelModality(
                "总结这个 PDF",
                listOf(ChatAttachmentReference("report.pdf", "content://report", image = false)),
                listOf(ModelAttachment("report-page-1.jpg", "data:image/jpeg;base64,AA", "image/jpeg")),
            ),
        )
    }

    @Test
    fun visionFallsBackOnlyToKnownCapableOrCustomModel() {
        assertEquals("", resolveVisionModel(ApiConfig(providerId = "deepseek", model = "deepseek-v4-flash")))
        assertEquals("gemini-3.5-flash", resolveVisionModel(ApiConfig(providerId = "gemini", model = "gemini-3.5-flash")))
        assertEquals("my-vision", resolveVisionModel(ApiConfig(visionModel = "my-vision")))
    }

    @Test
    fun attachmentUrisAreNotSentToModelButSkillInstructionsRemain() {
        val content = """分析图片

---
Mason 附加上下文
- Skill：视觉检查 | /skill
<mason-skill-instructions>
检查画面中的风险
</mason-skill-instructions>
- 图片：screen.png | content://private/screen
请结合以上材料处理
""".trimIndent()
        val context = ChatContextParser.parse(content)

        val sanitized = sanitizeAttachmentMetadata(listOf(ChatMessage("user", content)), context).single().content.orEmpty()

        assertFalse(sanitized.contains("content://private/screen"))
        assertTrue(sanitized.contains("检查画面中的风险"))
    }

    @Test
    fun largeImagesUsePowerOfTwoDecodeSampling() {
        assertEquals(4, calculateImageSampleSize(8_000, 4_000, 2_048))
        assertEquals(1, calculateImageSampleSize(1_920, 1_080, 2_048))
    }

    @Test
    fun explicitLocalSelectionNeverSwitchesBetweenLocalModels() {
        val miniCpm = ApiConfig(
            localModel = "minicpm5-1b-q4-k-m-gguf",
            localModelDirectEnabled = true,
        )
        val gemma = miniCpm.copy(localModel = "gemma-4-e2b-it-litert")

        assertEquals("minicpm5-1b-q4-k-m-gguf", resolveSelectedLocalModelId(miniCpm))
        assertEquals("gemma-4-e2b-it-litert", resolveSelectedLocalModelId(gemma))
    }

    @Test
    fun remoteFallbackUsesOnlyTheConfiguredLocalModel() {
        val config = ApiConfig(
            localModel = "minicpm5-1b-q4-k-m-gguf",
            offlineFallbackEnabled = true,
        )

        assertEquals(
            "minicpm5-1b-q4-k-m-gguf",
            resolveLocalFallbackModelId(config, ModelModality.Text, useLocalDirect = false, localReady = true),
        )
        assertEquals(
            null,
            resolveLocalFallbackModelId(config, ModelModality.Vision, useLocalDirect = false, localReady = true),
        )
    }

    @Test
    fun dynamicRoutingUsesLocalOnlyForSimpleText() {
        val config = ApiConfig(dynamicLocalRoutingEnabled = true)

        assertTrue(
            shouldUseLocalModel(
                config = config,
                modality = ModelModality.Text,
                userText = "帮我把这句话改得简洁一点",
                hasAttachments = false,
                hasSkill = false,
                localReady = true,
                localEngineAvailable = true,
            ),
        )
        assertFalse(isSimpleLocalRequest("请读取手机联系人并发送短信"))
        assertFalse(isSimpleLocalRequest("请详细分析并制定一个分步骤的完整方案"))
        assertTrue(isConversationDispatchRequest("把总结发送到 MiniCPM 调试对话"))
        assertFalse(isSimpleLocalRequest("把总结发送到 MiniCPM 调试对话"))
        assertFalse(
            shouldUseLocalModel(
                config = config,
                modality = ModelModality.Vision,
                userText = "看看这张图",
                hasAttachments = true,
                hasSkill = false,
                localReady = true,
                localEngineAvailable = true,
            ),
        )
    }

    @Test
    fun localOnlyConfigurationRoutesDeviceInfoIntentLocally() {
        val localOnly = ApiConfig(localModel = "minicpm5-1b-q4-k-m-gguf")

        assertTrue(
            shouldUseLocalModel(
                config = localOnly,
                modality = ModelModality.Text,
                userText = "检测本机手机信息",
                hasAttachments = false,
                hasSkill = false,
                localReady = true,
                localEngineAvailable = true,
            ),
        )
    }

    @Test
    fun configuredRemoteModelKeepsPhoneIntentOnRemoteByDefault() {
        val connection = ApiConnection(
            id = "remote",
            providerId = "openai",
            name = "Remote",
            apiUrl = "https://example.invalid/v1",
            apiKey = "test-key",
            modelIds = listOf("remote-model"),
            modelCapabilities = mapOf(
                "remote-model" to ApiModelCapabilities(supportsChat = true),
            ),
        )
        val config = ApiConfig(
            connections = listOf(connection),
            chatModelRef = ModelReference(connection.id, "remote-model"),
            localModel = "minicpm5-1b-q4-k-m-gguf",
        )

        assertFalse(
            shouldUseLocalModel(
                config = config,
                modality = ModelModality.Text,
                userText = "检测本机手机信息",
                hasAttachments = false,
                hasSkill = false,
                localReady = true,
                localEngineAvailable = true,
            ),
        )
    }

    @Test
    fun offlineFallbackDoesNotHideAuthenticationErrors() {
        assertTrue(isOfflineFailure("failed to connect to host"))
        assertTrue(isOfflineFailure("模型请求失败：UnknownHostException"))
        assertTrue(isOfflineFailure("API 错误 503: unavailable"))
        assertFalse(isOfflineFailure("API 错误 401: invalid key"))
    }

    private fun routingConfig(dynamic: Boolean): ApiConfig {
        val deepseek = ApiConnection(
            id = "deepseek",
            providerId = "deepseek",
            name = "DeepSeek",
            apiUrl = "https://api.deepseek.com",
            apiKey = "key",
            modelIds = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
            modelCapabilities = mapOf(
                "deepseek-v4-flash" to ApiModelCapabilities(supportsChat = true, supportsTools = true),
                "deepseek-v4-pro" to ApiModelCapabilities(supportsChat = true, supportsTools = true),
            ),
        )
        val qwen = ApiConnection(
            id = "qwen",
            providerId = "qwen",
            name = "Qwen",
            apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            apiKey = "key",
            modelIds = listOf("qwen-turbo"),
            modelCapabilities = mapOf(
                "qwen-turbo" to ApiModelCapabilities(supportsChat = true, supportsTools = true),
            ),
        )
        val mimo = ApiConnection(
            id = "mimo",
            providerId = "mimo",
            name = "MiMo",
            apiUrl = "https://api.xiaomimimo.com/v1",
            apiKey = "key",
            modelIds = listOf("mimo-v2.5"),
            modelCapabilities = mapOf(
                "mimo-v2.5" to ApiModelCapabilities(supportsChat = true, supportsTools = false),
            ),
        )
        return ApiConfig(
            providerId = deepseek.providerId,
            apiUrl = deepseek.apiUrl,
            apiKey = deepseek.apiKey,
            model = "deepseek-v4-flash",
            connections = listOf(deepseek, qwen, mimo),
            chatModelRef = ModelReference(deepseek.id, "deepseek-v4-flash"),
            dynamicLocalRoutingEnabled = dynamic,
        )
    }
}
