package com.denggl2.mason.ui.settings

import com.denggl2.mason.agent.annotateTaskRun
import com.denggl2.mason.agent.createTaskRun
import com.denggl2.mason.crashguard.data.CrashRecord
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConnection
import com.denggl2.mason.data.ModelContribution
import com.denggl2.mason.data.ModelReference
import com.denggl2.mason.data.annotateModelParticipation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {
    @Test
    fun redaction_removesConfiguredBearerNamedAndUrlSecrets() {
        val report = buildDiagnosticReport(
            baseInput(
                config = ApiConfig(
                    connections = listOf(
                        ApiConnection(
                            id = "custom",
                            providerId = "custom",
                            name = "测试中转",
                            apiUrl = "https://relay.example/v1?token=query-secret&region=cn",
                            apiKey = "sk-configured-secret",
                            modelIds = listOf("test-model"),
                        ),
                    ),
                    chatModelRef = ModelReference("custom", "test-model"),
                ),
                conversations = listOf(
                    DiagnosticConversation(
                        id = 1,
                        title = "排错",
                        updatedAt = 2,
                        messages = listOf(
                            DiagnosticMessage(
                                role = "user",
                                content = "API Key: sk-configured-secret\nAuthorization: Bearer bearer-secret-123\npassword=hunter2",
                                timestamp = 2,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertFalse(report.contains("sk-configured-secret"))
        assertFalse(report.contains("bearer-secret-123"))
        assertFalse(report.contains("query-secret"))
        assertFalse(report.contains("hunter2"))
        assertTrue(report.contains("[REDACTED]"))
    }

    @Test
    fun report_containsDiagnosticSectionsAndStripsHiddenMessageMarkers() {
        val run = createTaskRun("调查崩溃", now = 10)
        val annotated = annotateTaskRun(
            annotateModelParticipation(
                "用户可见回答",
                listOf(ModelContribution("model-a", "remote", listOf("生成回答"))),
            ),
            run,
        )
        val report = buildDiagnosticReport(
            baseInput(
                conversations = listOf(
                    DiagnosticConversation(
                        id = 3,
                        title = "测试对话",
                        updatedAt = 20,
                        messages = listOf(DiagnosticMessage("assistant", annotated, 20)),
                    ),
                ),
                taskRuns = listOf(run),
                crashes = listOf(
                    CrashRecord(
                        timestamp = 30,
                        threadName = "main",
                        exceptionType = "IllegalStateException",
                        message = "test crash",
                        stackTrace = "stack line",
                        appVersion = "0.2.0",
                        isLaunchCrash = false,
                    ),
                ),
            ),
        )

        assertTrue(report.contains("[应用与设备]"))
        assertTrue(report.contains("[模型配置]"))
        assertTrue(report.contains("[最近对话]"))
        assertTrue(report.contains("用户可见回答"))
        assertTrue(report.contains("[最近任务]"))
        assertTrue(report.contains("调查崩溃"))
        assertTrue(report.contains("[崩溃与 ANR]"))
        assertTrue(report.contains("IllegalStateException"))
        assertFalse(report.contains("mason-model-participation"))
        assertFalse(report.contains("mason-task-run"))
    }

    @Test
    fun urlSanitizer_preservesEndpointButRedactsEveryQueryValue() {
        val sanitized = sanitizeDiagnosticUrl(
            "https://example.com/v1/models?api_key=secret&region=cn#fragment",
        )

        assertTrue(sanitized.startsWith("https://example.com/v1/models?"))
        assertTrue(sanitized.contains("api_key=[REDACTED]"))
        assertTrue(sanitized.contains("region=[REDACTED]"))
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("#fragment"))
    }

    private fun baseInput(
        config: ApiConfig = ApiConfig(),
        conversations: List<DiagnosticConversation> = emptyList(),
        taskRuns: List<com.denggl2.mason.agent.TaskRun> = emptyList(),
        crashes: List<CrashRecord> = emptyList(),
    ) = DiagnosticReportInput(
        generatedAt = 1,
        appVersion = "0.2.0",
        device = DiagnosticDeviceInfo(
            manufacturer = "Mason",
            model = "Test",
            androidVersion = "16",
            sdk = 36,
            supportedAbis = listOf("arm64-v8a"),
            locale = "zh-CN",
            totalMemoryBytes = 8L * 1024 * 1024 * 1024,
            availableMemoryBytes = 4L * 1024 * 1024 * 1024,
            availableStorageBytes = 16L * 1024 * 1024 * 1024,
        ),
        config = config,
        localModels = emptyList(),
        conversations = conversations,
        taskRuns = taskRuns,
        crashes = crashes,
    )
}
