package com.denggl2.mason.ui.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiTestRuntimeTest {
    @Test
    fun cancellingTestRejectsLateResultAndKeepsDraftUnsaved() = runBlocking {
        val runtime = ApiTestRuntime()
        val started = CompletableDeferred<Long>()
        val keepRunning = CompletableDeferred<Unit>()

        assertTrue(
            runtime.launch { runId ->
                runtime.update(
                    runId,
                    ApiTestUiState(isTesting = true, message = "正在测试模型..."),
                )
                started.complete(runId)
                keepRunning.await()
            },
        )
        val runId = withTimeout(1_000) { started.await() }

        assertTrue(runtime.cancelActiveTest())
        assertFalse(runtime.current.isTesting)
        assertFalse(runtime.current.saved)
        assertEquals("已取消测试，配置未保存", runtime.current.message)
        assertFalse(
            runtime.update(
                runId,
                ApiTestUiState(success = true, saved = true, message = "不应写入"),
            ),
        )
        assertEquals("已取消测试，配置未保存", runtime.current.message)
    }
}
