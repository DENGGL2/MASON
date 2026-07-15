package com.denggl2.mason.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.denggl2.mason.data.AutomationPreferencesDataStore
import com.denggl2.mason.data.ArtifactStore
import com.denggl2.mason.data.MasonAutomationRunLog
import com.denggl2.mason.data.MasonAutomationStepLog
import com.denggl2.mason.data.MasonAutomationSpec
import com.denggl2.mason.data.SkillAutomationStore
import com.denggl2.mason.agent.GovernedToolExecutor
import com.denggl2.mason.agent.ToolExecutionContext
import com.denggl2.mason.agent.ToolExecutionSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class AutomationRunResult(
    val spec: MasonAutomationSpec,
    val log: MasonAutomationRunLog,
)

@Singleton
class AutomationRunner @Inject constructor(
    private val store: SkillAutomationStore,
    private val toolExecutor: GovernedToolExecutor,
    private val preferencesStore: AutomationPreferencesDataStore,
    private val modelGenerator: AutomationModelGenerator,
    private val artifactStore: ArtifactStore,
    private val orchestrator: AutomationOrchestrator,
) {
    suspend fun run(
        automationId: String,
        source: String,
        initialValues: Map<String, String> = emptyMap(),
        stopAfterActionId: String? = null,
    ): AutomationRunResult {
        val spec = checkNotNull(store.automation(automationId)) { "没有找到自动化" }
        orchestrator.plan(spec)
        check(source == SOURCE_MANUAL || spec.enabled) { "自动化已停用" }
        if (source != SOURCE_MANUAL) {
            check(preferencesStore.preferences.first().backgroundExecutionEnabled) {
                "后台自动化已关闭"
            }
        }
        var failure: String? = null
        var artifactPath: String? = null
        val values = initialValues.toMutableMap()
        val stepLogs = mutableListOf<MasonAutomationStepLog>()
        try {
            for ((index, action) in spec.actions.withIndex()) {
                val actionId = action.id.ifBlank { "step-${index + 1}" }
                val actionTitle = action.title.ifBlank { action.defaultTitle() }
                val stepStartedAt = System.currentTimeMillis()
                if (!AutomationWorkflowLogic.conditionMatches(action.condition, values)) {
                    stepLogs += MasonAutomationStepLog(
                        actionId = actionId,
                        title = actionTitle,
                        status = "skipped",
                        message = "条件不满足，已跳过",
                        startedAt = stepStartedAt,
                    )
                    continue
                }
                if (source == SOURCE_SCHEDULE && action.type !in BACKGROUND_ACTIONS) {
                    error("后台定时不支持 ${action.type} 动作")
                }
                val stepResult = runCatching {
                    when (action.type) {
                        ACTION_TOOL -> {
                            val toolName = action.arguments["tool_name"].orEmpty()
                            check(toolName.isNotBlank()) { "步骤缺少工具名称" }
                            if (source == SOURCE_SCHEDULE) {
                                check(toolName in BACKGROUND_SAFE_TOOLS) { "后台不支持 $toolName 工具" }
                            }
                            val args = action.arguments
                                .filterKeys { it != "tool_name" }
                                .mapValues { (_, value) -> AutomationWorkflowLogic.interpolate(value, values) }
                            val result = executeTool(toolName, args, source)
                            check(result.success) { result.error ?: "$toolName 执行失败" }
                            result.data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                        }
                        ACTION_MODEL_ARTIFACT -> {
                            val legacyContext = when (action.arguments["context_tool"]) {
                                CONTEXT_CALENDAR -> executeTool("calendar", mapOf("action" to "list"), source)
                                null, "" -> null
                                else -> error("不支持的自动化资料来源：${action.arguments["context_tool"]}")
                            }
                            check(legacyContext?.success != false) {
                                legacyContext?.error ?: "读取自动化资料失败"
                            }
                            val input = action.inputKey.takeIf(String::isNotBlank)?.let(values::get).orEmpty()
                                .ifBlank {
                                    legacyContext?.data?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty()
                                }
                            val generationPrompt = buildString {
                                append(AutomationWorkflowLogic.interpolate(action.arguments["prompt"].orEmpty(), values))
                                if (input.isNotBlank()) append("\n\n以下是本次运行读取到的资料：\n$input")
                            }
                            val generation = modelGenerator.generate(generationPrompt)
                            val artifact = artifactStore.saveTextArtifact(
                                fileName = action.arguments["file_name"].orEmpty()
                                    .let { AutomationWorkflowLogic.interpolate(it, values) }
                                    .ifBlank { "automation-output.md" },
                                content = generation.content,
                            )
                            artifactPath = artifact.path
                            values["artifact_name"] = artifact.name
                            values["artifact_path"] = artifact.path
                            values["model_output"] = generation.content
                            "已生成 ${artifact.name}"
                        }
                        ACTION_SKILL -> {
                            val skillId = action.arguments["skill_id"].orEmpty()
                            val skill = store.listInstalledSkills(enabledOnly = true)
                                .firstOrNull { it.manifest.id == skillId }
                                ?: error("Skill 未安装或已停用：$skillId")
                            val input = action.inputKey.takeIf(String::isNotBlank)?.let(values::get).orEmpty()
                            val prompt = buildString {
                                append("按以下 Skill 说明完成任务，只输出结果正文。\n\n")
                                append(skill.instructions)
                                append("\n\n任务：")
                                append(action.arguments["prompt"].orEmpty())
                                if (input.isNotBlank()) append("\n\n输入：\n$input")
                            }
                            modelGenerator.generate(prompt).content
                        }
                        "notification", "launch_app" -> {
                            val result = executeTool(
                                action.type,
                                action.arguments.mapValues { (_, value) -> AutomationWorkflowLogic.interpolate(value, values) },
                                source,
                            )
                            check(result.success) { result.error ?: "${action.type} 执行失败" }
                            result.data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                                .ifBlank { "执行成功" }
                        }
                        else -> error("不支持的自动化动作：${action.type}")
                    }
                }
                stepResult.onSuccess { output ->
                    if (action.outputKey.isNotBlank()) values[action.outputKey] = output
                    stepLogs += MasonAutomationStepLog(
                        actionId = actionId,
                        title = actionTitle,
                        status = "success",
                        message = output.take(500),
                        startedAt = stepStartedAt,
                    )
                }.onFailure { error ->
                    stepLogs += MasonAutomationStepLog(
                        actionId = actionId,
                        title = actionTitle,
                        status = "failed",
                        message = error.message ?: "执行失败",
                        startedAt = stepStartedAt,
                    )
                    if (!action.continueOnFailure) failure = error.message ?: "执行失败"
                }
                if (failure != null) break
                if (stopAfterActionId != null && actionId == stopAfterActionId) break
            }
            val hasExplicitNotification = spec.actions.any { it.type == "notification" }
            if (failure == null && artifactPath != null && !hasExplicitNotification) {
                executeTool(
                    "notification",
                    mapOf("title" to spec.name, "text" to "已生成 ${values["artifact_name"].orEmpty()}"),
                    source,
                )
            }
        } catch (error: Exception) {
            failure = error.message ?: "自动化执行失败"
        }
        val review = orchestrator.review(stepLogs, artifactPath)
        val log = MasonAutomationRunLog(
            automationId = automationId,
            status = if (failure == null) "success" else "failed",
            message = orchestrator.summarize(spec, failure ?: review, failure != null),
            source = source,
            artifactPath = artifactPath,
            steps = stepLogs,
        )
        store.appendAutomationLog(log)
        return AutomationRunResult(spec, log)
    }

    private suspend fun executeTool(
        name: String,
        args: Map<String, String>,
        source: String,
    ) = toolExecutor.execute(
        name,
        args,
        ToolExecutionContext(
            source = ToolExecutionSource.Automation,
            userConfirmed = true,
            background = source != SOURCE_MANUAL,
        ),
    )

    suspend fun runThroughStep(automationId: String, actionId: String): AutomationRunResult =
        run(
            automationId = automationId,
            source = SOURCE_MANUAL,
            stopAfterActionId = actionId,
        )

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_SCHEDULE = "schedule"
        const val SOURCE_EVENT = "event"
        const val ACTION_MODEL_ARTIFACT = "model_artifact"
        const val ACTION_TOOL = "tool"
        const val ACTION_SKILL = "skill"
        private const val CONTEXT_CALENDAR = "calendar"
        private val BACKGROUND_ACTIONS = setOf(
            "notification", ACTION_MODEL_ARTIFACT, ACTION_TOOL, ACTION_SKILL,
        )
        private val BACKGROUND_SAFE_TOOLS = setOf(
            "calendar", "location", "network_info", "battery", "get_wifi_info", "get_bluetooth_info",
        )
    }

    private fun com.denggl2.mason.data.MasonAutomationAction.defaultTitle(): String =
        AutomationWorkflowLogic.defaultTitle(type)

}

@Singleton
class AutomationScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val store: SkillAutomationStore,
    private val preferencesStore: AutomationPreferencesDataStore,
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun sync(spec: MasonAutomationSpec) {
        sync(spec, preferencesStore.preferences.first().backgroundExecutionEnabled)
    }

    suspend fun syncAll() {
        syncAll(preferencesStore.preferences.first().backgroundExecutionEnabled)
    }

    suspend fun syncAll(backgroundExecutionEnabled: Boolean) {
        store.listAutomations().forEach { spec ->
            sync(spec, backgroundExecutionEnabled)
        }
    }

    suspend fun reconcileAtStartup() {
        if (!preferencesStore.preferences.first().backgroundExecutionEnabled) {
            syncAll(backgroundExecutionEnabled = false)
        }
    }

    private fun sync(spec: MasonAutomationSpec, backgroundExecutionEnabled: Boolean) {
        if (!backgroundExecutionEnabled || !spec.enabled ||
            spec.trigger.type !in SCHEDULED_TRIGGERS + EVENT_TRIGGERS
        ) {
            cancel(spec.id)
            return
        }
        cancel(spec.id)
        when (spec.trigger.type) {
            TRIGGER_INTERVAL -> scheduleInterval(spec)
            TRIGGER_DAILY, TRIGGER_WEEKDAYS -> scheduleTimed(spec)
            TRIGGER_LOCATION -> scheduleLocationCheck(spec)
            TRIGGER_CHARGING, TRIGGER_WIFI -> scheduleEventPoll(spec)
        }
    }

    private fun scheduleEventPoll(spec: MasonAutomationSpec) {
        val request = PeriodicWorkRequestBuilder<AutomationEventPollWorker>(
            MIN_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setInputData(workDataOf(KEY_AUTOMATION_ID to spec.id))
            .setConstraints(workConstraints(spec))
            .addTag(workTag(spec.id))
            .build()
        workManager.enqueueUniquePeriodicWork(
            workName(spec.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun scheduleLocationCheck(spec: MasonAutomationSpec) {
        val request = PeriodicWorkRequestBuilder<AutomationLocationWorker>(
            MIN_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setInputData(workDataOf(KEY_AUTOMATION_ID to spec.id))
            .setConstraints(workConstraints(spec))
            .addTag(workTag(spec.id))
            .build()
        workManager.enqueueUniquePeriodicWork(
            workName(spec.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    suspend fun scheduleNext(spec: MasonAutomationSpec) {
        if (spec.trigger.type !in TIMED_TRIGGERS) return
        val backgroundEnabled = preferencesStore.preferences.first().backgroundExecutionEnabled
        val current = store.automation(spec.id)
        if (backgroundEnabled && current?.enabled == true && current.trigger == spec.trigger) {
            scheduleTimed(current)
        }
    }

    private fun scheduleInterval(spec: MasonAutomationSpec) {
        val minutes = spec.trigger.value.toLongOrNull()?.coerceAtLeast(MIN_INTERVAL_MINUTES)
            ?: error("定时间隔格式不正确")
        val request = PeriodicWorkRequestBuilder<AutomationWorker>(minutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_AUTOMATION_ID to spec.id))
            .setConstraints(workConstraints(spec))
            .addTag(workTag(spec.id))
            .build()
        workManager.enqueueUniquePeriodicWork(
            workName(spec.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun scheduleTimed(spec: MasonAutomationSpec) {
        val runAt = nextRunAt(spec.trigger.type, spec.trigger.value)
        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInitialDelay((runAt - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_AUTOMATION_ID to spec.id))
            .setConstraints(workConstraints(spec))
            .addTag(workTag(spec.id))
            .build()
        workManager.enqueueUniqueWork(
            "${workName(spec.id)}-${request.id}",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun workConstraints(spec: MasonAutomationSpec): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            when (spec.constraints.network) {
                NETWORK_CONNECTED -> NetworkType.CONNECTED
                NETWORK_UNMETERED -> NetworkType.UNMETERED
                else -> NetworkType.NOT_REQUIRED
            },
        )
        .setRequiresCharging(spec.constraints.requiresCharging)
        .setRequiresBatteryNotLow(spec.constraints.requiresBatteryNotLow)
        .build()

    fun cancel(automationId: String) {
        workManager.cancelUniqueWork(workName(automationId))
        workManager.cancelAllWorkByTag(workTag(automationId))
    }

    companion object {
        const val TRIGGER_INTERVAL = "interval"
        const val TRIGGER_DAILY = "daily"
        const val TRIGGER_WEEKDAYS = "weekdays"
        const val TRIGGER_CHARGING = "charging"
        const val TRIGGER_WIFI = "wifi"
        const val TRIGGER_BLUETOOTH = "bluetooth"
        const val TRIGGER_NOTIFICATION = "notification"
        const val TRIGGER_LOCATION = "location"
        const val MIN_INTERVAL_MINUTES = 15L
        const val KEY_AUTOMATION_ID = "automation_id"
        const val NETWORK_NONE = "none"
        const val NETWORK_CONNECTED = "connected"
        const val NETWORK_UNMETERED = "unmetered"
        private val TIMED_TRIGGERS = setOf(TRIGGER_DAILY, TRIGGER_WEEKDAYS)
        private val SCHEDULED_TRIGGERS = TIMED_TRIGGERS + TRIGGER_INTERVAL
        private val EVENT_TRIGGERS = setOf(
            TRIGGER_CHARGING,
            TRIGGER_WIFI,
            TRIGGER_BLUETOOTH,
            TRIGGER_NOTIFICATION,
            TRIGGER_LOCATION,
        )
        private val DEFAULT_WEEKDAYS = (1..5).toSet()
        private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        private fun workName(id: String) = "mason-automation-$id"
        private fun workTag(id: String) = "mason-automation-tag-$id"

        fun nextRunAt(triggerType: String, value: String, now: Long = System.currentTimeMillis()): Long {
            val parts = scheduledTime(value).split(':')
            val hour = parts.getOrNull(0)?.toIntOrNull()
            val minute = parts.getOrNull(1)?.toIntOrNull()
            require(hour != null && hour in 0..23 && minute != null && minute in 0..59) {
                "固定时间格式不正确"
            }
            val candidate = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis <= now) candidate.add(Calendar.DAY_OF_YEAR, 1)
            if (triggerType == TRIGGER_WEEKDAYS) {
                val weekdays = selectedWeekdays(value)
                require(weekdays.isNotEmpty()) { "至少选择一个星期" }
                while (calendarDayToWeekday(candidate.get(Calendar.DAY_OF_WEEK)) !in weekdays) {
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return candidate.timeInMillis
        }

        fun selectedWeekdays(value: String): Set<Int> {
            if ('@' !in value) return DEFAULT_WEEKDAYS
            return value.substringBefore('@')
                .split(',')
                .mapNotNull(String::toIntOrNull)
                .filter { it in 1..7 }
                .toSet()
        }

        fun scheduledTime(value: String): String = value.substringAfter('@', value)

        fun encodeWeekdays(weekdays: Set<Int>, time: String): String =
            "${weekdays.filter { it in 1..7 }.sorted().joinToString(",")}@$time"

        fun describeWeekdays(value: String): String {
            val days = selectedWeekdays(value).sorted().joinToString("、") { day ->
                WEEKDAY_LABELS[day - 1]
            }
            return "$days ${scheduledTime(value)}"
        }

        private fun calendarDayToWeekday(calendarDay: Int): Int =
            if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
    }
}

class AutomationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val automationId = inputData.getString(AutomationScheduler.KEY_AUTOMATION_ID)
            ?: return Result.failure(workDataOf("error" to "缺少自动化 ID"))
        val runner = EntryPointAccessors.fromApplication(
            applicationContext,
            AutomationWorkerEntryPoint::class.java,
        )
        return runCatching {
            val outcome = runner.automationRunner().run(automationId, AutomationRunner.SOURCE_SCHEDULE)
            runner.automationScheduler().scheduleNext(outcome.spec)
            if (outcome.log.status == "success") Result.success() else Result.failure(
                workDataOf("error" to outcome.log.message),
            )
        }.getOrElse { error ->
            Result.failure(workDataOf("error" to (error.message ?: "自动化执行失败")))
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomationWorkerEntryPoint {
    fun automationRunner(): AutomationRunner
    fun automationScheduler(): AutomationScheduler
}
