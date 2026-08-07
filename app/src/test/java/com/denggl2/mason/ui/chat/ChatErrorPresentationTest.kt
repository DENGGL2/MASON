package com.denggl2.mason.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatErrorPresentationTest {

    @Test
    fun `rate limit error keeps concise provider details without source url`() {
        val summary = summarizeModelError(
            "生图 API 错误 429: {\"error\":{\"code\":\"RateLimitReached\",\"message\":\"Your requests exceeded the call rate limit. Please retry after 3 seconds. See https://aka.ms/oai/quotaincrease\"}}",
        )

        assertTrue(summary.contains("HTTP 429"))
        assertTrue(summary.contains("RateLimitReached"))
        assertTrue(summary.contains("服务商限流"))
        assertTrue(summary.contains("链接已省略"))
        assertTrue(summary.length < 500)
    }

    @Test
    fun `diagnostic error urls are excluded from source extraction input`() {
        val visible = stripDiagnosticErrorLines(
            "请求失败：HTTP 429 https://aka.ms/oai/quotaincrease\n正常参考：https://example.com/help",
        )

        assertFalse(visible.contains("aka.ms"))
        assertTrue(visible.contains("example.com"))
    }

    @Test
    fun `transient model failures are retryable but invalid request is not`() {
        assertTrue(isRetryableModelError("生图 API 错误 429: RateLimitReached"))
        assertTrue(isRetryableModelError("模型请求失败：timeout"))
        assertTrue(isRetryableModelError("HTTP 503 temporarily unavailable"))
        assertFalse(isRetryableModelError("生图 API 错误 400: invalid_request_error"))
        assertFalse(isRetryableModelError("生图 API 错误 404: model_not_found"))
    }
}
