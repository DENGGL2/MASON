package com.denggl2.mason.automation

import com.denggl2.mason.data.AutomationPreferencesDataStore
import com.denggl2.mason.data.MasonAutomationAction
import com.denggl2.mason.data.MasonAutomationConstraints
import com.denggl2.mason.data.MasonAutomationSpec
import com.denggl2.mason.data.MasonAutomationTrigger
import com.denggl2.mason.data.SkillAutomationStore
import com.denggl2.mason.agent.GovernedToolExecutor
import com.denggl2.mason.agent.ToolExecutionContext
import com.denggl2.mason.agent.ToolExecutionSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AutomationDraft(
    val draftId: String,
    val sourceRequest: String,
    val name: String,
    val triggerType: String,
    val triggerValue: String = "",
    val actionType: String,
    val arguments: Map<String, String>,
    val constraints: MasonAutomationConstraints = MasonAutomationConstraints(),
    val summary: String,
    val actions: List<MasonAutomationAction> = emptyList(),
    val operation: String = "create",
    val targetAutomationId: String = "",
    val warnings: List<String> = emptyList(),
)

data class AutomationDraftResult(
    val draft: AutomationDraft? = null,
    val clarificationQuestion: String? = null,
)

@Serializable
data class AutomationApplyResult(
    val draftId: String,
    val automationId: String,
    val status: String,
    val message: String,
    val artifactPath: String? = null,
    val scheduleActive: Boolean = false,
)

@Serializable
private data class ModelAutomationDraft(
    @SerialName("needs_clarification") val needsClarification: Boolean = false,
    val question: String = "",
    val name: String = "",
    @SerialName("trigger_type") val triggerType: String = "",
    @SerialName("trigger_value") val triggerValue: String = "",
    @SerialName("action_type") val actionType: String = "",
    val title: String = "",
    val text: String = "",
    val prompt: String = "",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("context_source") val contextSource: String = "",
    @SerialName("package_name") val packageName: String = "",
    val network: String = AutomationScheduler.NETWORK_NONE,
    @SerialName("requires_charging") val requiresCharging: Boolean = false,
    @SerialName("requires_battery_not_low") val requiresBatteryNotLow: Boolean = false,
    val actions: List<ModelAutomationStep> = emptyList(),
)

@Serializable
private data class ModelAutomationStep(
    val id: String = "",
    val title: String = "",
    val type: String,
    val arguments: Map<String, String> = emptyMap(),
    @SerialName("input_key") val inputKey: String = "",
    @SerialName("output_key") val outputKey: String = "",
    @SerialName("condition_key") val conditionKey: String = "",
    @SerialName("condition_operator") val conditionOperator: String = "not_empty",
    @SerialName("condition_value") val conditionValue: String = "",
    @SerialName("continue_on_failure") val continueOnFailure: Boolean = false,
)

@Singleton
class AutomationDraftService @Inject constructor(
    private val modelGenerator: AutomationModelGenerator,
    private val store: SkillAutomationStore,
    private val scheduler: AutomationScheduler,
    private val runner: AutomationRunner,
    private val preferencesStore: AutomationPreferencesDataStore,
    private val toolExecutor: GovernedToolExecutor,
    private val capabilityInspector: AutomationCapabilityInspector,
) {
    suspend fun draftFromNaturalLanguage(request: String): AutomationDraftResult {
        inferManagementDraft(request)?.let { return it }
        inferLocationDraft(request)?.let { inferred -> return validateDraft(request, inferred) }
        inferSkillDraft(request)?.let { return it }
        if (!AutomationWorkflowLogic.requiresModelPlanner(request)) {
            inferCommonDraft(request)?.let { inferred ->
                return validateDraft(request, inferred)
            }
        }
        val generation = modelGenerator.generateStructured(draftPrompt(request))
        val modelDraft = parseModelDraft(generation.content) ?: inferCommonDraft(request) ?: run {
            val repaired = modelGenerator.generateStructured(
                "把下面内容修复成请求要求的单个有效 JSON 对象，只返回 JSON：\n${generation.content.take(12_000)}",
            )
            parseModelDraft(repaired.content)
        }
            ?: return AutomationDraftResult(
                clarificationQuestion = "我没能可靠识别这条自动化。请补充什么时候运行，以及要执行什么操作。",
            )
        if (modelDraft.needsClarification) {
            inferCommonDraft(request)?.let { inferred ->
                return validateDraft(request, inferred)
            }
            return AutomationDraftResult(
                clarificationQuestion = modelDraft.question.ifBlank {
                    "还缺少触发时间或执行内容，请补充后再创建。"
                },
            )
        }
        return validateDraft(request, modelDraft)
    }

    suspend fun applyDraft(draft: AutomationDraft, runTest: Boolean): AutomationApplyResult {
        if (draft.operation != OPERATION_CREATE) return applyManagementDraft(draft, runTest)
        val automationId = "automation-${draft.draftId.filter(Char::isLetterOrDigit).take(16).lowercase()}"
        val existing = store.automation(automationId)
        val spec = existing ?: MasonAutomationSpec(
            id = automationId,
            name = draft.name,
            description = draft.summary,
            enabled = true,
            trigger = MasonAutomationTrigger(draft.triggerType, draft.triggerValue),
            actions = draft.actions.ifEmpty { listOf(MasonAutomationAction(draft.actionType, draft.arguments)) },
            constraints = draft.constraints,
        )
        val saved = store.saveAutomation(spec)
        scheduler.sync(saved)
        val backgroundEnabled = preferencesStore.preferences.first().backgroundExecutionEnabled
        val scheduleActive = backgroundEnabled && saved.enabled &&
            saved.trigger.type != TRIGGER_MANUAL
        val testResult = if (runTest) runner.run(saved.id, AutomationRunner.SOURCE_MANUAL) else null
        val testSucceeded = testResult?.log?.status != "failed"
        val status = when {
            testResult == null -> "success"
            testSucceeded -> "success"
            else -> "test_failed"
        }
        val message = buildString {
            append(if (existing == null) "自动化已创建" else "自动化已存在")
            if (testResult != null) {
                append(if (testSucceeded) "，测试运行成功" else "，但测试运行失败：${testResult.log.message}")
            }
            if (saved.trigger.type != TRIGGER_MANUAL && !backgroundEnabled) {
                append("。后台自动化总开关当前关闭，开启后才会按时运行")
            }
        }
        return AutomationApplyResult(
            draftId = draft.draftId,
            automationId = saved.id,
            status = status,
            message = message,
            artifactPath = testResult?.log?.artifactPath,
            scheduleActive = scheduleActive,
        )
    }

    private suspend fun inferManagementDraft(request: String): AutomationDraftResult? {
        val operation = when {
            listOf("删除自动化", "删除这个自动化", "移除自动化").any(request::contains) -> OPERATION_ARCHIVE
            listOf("暂停自动化", "停用自动化", "关闭自动化").any(request::contains) -> OPERATION_DISABLE
            listOf("恢复自动化", "启用自动化", "开启自动化").any(request::contains) -> OPERATION_ENABLE
            listOf("修改自动化", "更新自动化", "改成", "改到").any(request::contains) -> OPERATION_UPDATE
            else -> return null
        }
        val automations = store.listAutomations()
        val matches = automations.filter {
            request.contains(it.name, ignoreCase = true) || request.contains(it.id, ignoreCase = true)
        }
        val target = when {
            matches.size == 1 -> matches.single()
            automations.size == 1 -> automations.single()
            else -> return AutomationDraftResult(
                clarificationQuestion = "请说出要操作的自动化名称。当前有：${automations.take(5).joinToString("、") { it.name }}",
            )
        }
        val updatedTrigger = if (operation == OPERATION_UPDATE) {
            extractTime(request)?.let { time -> target.trigger.copy(value = when (target.trigger.type) {
                AutomationScheduler.TRIGGER_WEEKDAYS -> AutomationScheduler.encodeWeekdays(
                    AutomationScheduler.selectedWeekdays(target.trigger.value), time,
                )
                else -> time
            }) } ?: target.trigger
        } else {
            target.trigger
        }
        val operationLabel = when (operation) {
            OPERATION_DISABLE -> "暂停"
            OPERATION_ENABLE -> "恢复"
            OPERATION_ARCHIVE -> "删除"
            else -> "更新"
        }
        return AutomationDraftResult(
            draft = AutomationDraft(
                draftId = UUID.randomUUID().toString(),
                sourceRequest = request,
                name = target.name,
                triggerType = updatedTrigger.type,
                triggerValue = updatedTrigger.value,
                actionType = target.actions.firstOrNull()?.type.orEmpty(),
                arguments = target.actions.firstOrNull()?.arguments.orEmpty(),
                constraints = target.constraints,
                summary = "$operationLabel“${target.name}”" + if (operation == OPERATION_UPDATE) {
                    "；新的运行时间为 ${updatedTrigger.value}"
                } else "",
                actions = target.actions,
                operation = operation,
                targetAutomationId = target.id,
            ),
        )
    }

    private suspend fun applyManagementDraft(draft: AutomationDraft, runTest: Boolean): AutomationApplyResult {
        val current = checkNotNull(store.automation(draft.targetAutomationId)) { "没有找到要操作的自动化" }
        val saved = when (draft.operation) {
            OPERATION_DISABLE -> store.saveAutomation(current.copy(enabled = false))
            OPERATION_ENABLE -> store.saveAutomation(current.copy(enabled = true))
            OPERATION_ARCHIVE -> checkNotNull(store.archiveAutomation(current.id))
            OPERATION_UPDATE -> store.saveAutomation(
                current.copy(
                    trigger = MasonAutomationTrigger(draft.triggerType, draft.triggerValue),
                    actions = draft.actions.ifEmpty { current.actions },
                    constraints = draft.constraints,
                ),
            )
            else -> error("不支持的自动化操作：${draft.operation}")
        }
        scheduler.sync(saved)
        val testResult = if (runTest && draft.operation in setOf(OPERATION_UPDATE, OPERATION_ENABLE)) {
            runner.run(saved.id, AutomationRunner.SOURCE_MANUAL)
        } else null
        val label = when (draft.operation) {
            OPERATION_DISABLE -> "已暂停"
            OPERATION_ENABLE -> "已恢复"
            OPERATION_ARCHIVE -> "已删除"
            else -> "已更新"
        }
        return AutomationApplyResult(
            draftId = draft.draftId,
            automationId = saved.id,
            status = if (testResult?.log?.status == "failed") "test_failed" else "success",
            message = "$label ${saved.name}" + if (testResult != null) {
                if (testResult.log.status == "success") "，测试成功" else "，测试失败：${testResult.log.message}"
            } else "",
            artifactPath = testResult?.log?.artifactPath,
            scheduleActive = saved.enabled &&
                preferencesStore.preferences.first().backgroundExecutionEnabled &&
                saved.trigger.type != TRIGGER_MANUAL,
        )
    }

    private suspend fun inferLocationDraft(request: String): ModelAutomationDraft? {
        val compact = request.replace(" ", "")
        val address = Regex("(?:到达|进入)(.+?)(?:时|后|就)").find(compact)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('“', '”', '"')
            ?.takeIf(String::isNotBlank)
            ?: return null
        val geocoded = toolExecutor.execute(
            "geocoding",
            mapOf("action" to "forward", "address" to address),
            ToolExecutionContext(ToolExecutionSource.Agent, userConfirmed = true),
        )
        if (!geocoded.success) return null
        val coordinates = Regex("\\((-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)\\)")
            .find(geocoded.data["results"].orEmpty())
            ?: return null
        val fileName = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.(?:md|txt|json|csv|html)", RegexOption.IGNORE_CASE)
            .find(request)?.value
        val generates = listOf("生成", "整理", "保存", "日报", "总结", "文件").any(compact::contains)
        val notifies = listOf("提醒", "通知").any(compact::contains)
        val actionType = when {
            generates -> AutomationRunner.ACTION_MODEL_ARTIFACT
            notifies -> ACTION_NOTIFICATION
            else -> return null
        }
        return ModelAutomationDraft(
            name = "到达 $address 时" + if (generates) "生成文件" else "提醒",
            triggerType = AutomationScheduler.TRIGGER_LOCATION,
            triggerValue = "${coordinates.groupValues[1]},${coordinates.groupValues[2]},200",
            actionType = actionType,
            title = if (actionType == ACTION_NOTIFICATION) "到达提醒" else "",
            text = if (actionType == ACTION_NOTIFICATION) request else "",
            prompt = if (generates) generationPrompt(compact) else "",
            fileName = fileName ?: "location-automation.md",
            requiresBatteryNotLow = true,
        )
    }

    private suspend fun inferSkillDraft(request: String): AutomationDraftResult? {
        val skill = store.listInstalledSkills(enabledOnly = true)
            .firstOrNull { installed ->
                request.contains(installed.manifest.name, ignoreCase = true) ||
                    request.contains(installed.manifest.id, ignoreCase = true)
            }
            ?: return null
        val base = inferCommonDraft(request) ?: return null
        val validated = validateDraft(request, base)
        val draft = validated.draft ?: return validated
        val skillOutput = "skill_result"
        val skillAction = MasonAutomationAction(
            id = "run-skill",
            title = "运行 ${skill.manifest.name}",
            type = AutomationRunner.ACTION_SKILL,
            arguments = mapOf(
                "skill_id" to skill.manifest.id,
                "prompt" to request,
            ),
            outputKey = skillOutput,
        )
        val nextActions = draft.actions.mapIndexed { index, action ->
            if (index == 0 && action.type == AutomationRunner.ACTION_MODEL_ARTIFACT) {
                action.copy(inputKey = skillOutput)
            } else action
        }
        return AutomationDraftResult(
            draft = draft.copy(
                actions = listOf(skillAction) + nextActions,
                summary = draft.summary + "；使用 ${skill.manifest.name}",
            ),
        )
    }

    private suspend fun validateDraft(request: String, model: ModelAutomationDraft): AutomationDraftResult {
        val triggerType = model.triggerType.trim().lowercase().let { type ->
            when (type) {
                TRIGGER_MANUAL, AutomationScheduler.TRIGGER_INTERVAL,
                AutomationScheduler.TRIGGER_DAILY, AutomationScheduler.TRIGGER_WEEKDAYS -> type
                AutomationScheduler.TRIGGER_CHARGING, AutomationScheduler.TRIGGER_WIFI,
                AutomationScheduler.TRIGGER_BLUETOOTH, AutomationScheduler.TRIGGER_NOTIFICATION,
                AutomationScheduler.TRIGGER_LOCATION -> type
                else -> ""
            }
        }
        if (triggerType.isBlank()) {
            return AutomationDraftResult(clarificationQuestion = "你希望它手动运行、每天运行、每周运行，还是每隔一段时间运行？")
        }
        val triggerValue = normalizeTriggerValue(triggerType, model.triggerValue)
            ?: return AutomationDraftResult(clarificationQuestion = triggerQuestion(triggerType))
        val actionType = model.actions.firstOrNull()?.type?.trim()?.lowercase()
            ?: model.actionType.trim().lowercase()
        if (actionType !in SUPPORTED_ACTIONS) {
            return AutomationDraftResult(
                clarificationQuestion = "目前可自动执行提醒、生成文件或手动打开 App。请说明要用哪一种。",
            )
        }
        if (triggerType != TRIGGER_MANUAL && actionType == ACTION_LAUNCH_APP) {
            return AutomationDraftResult(
                clarificationQuestion = "Android 不允许 Mason 在后台可靠地直接打开 App。要改成到点发提醒吗？",
            )
        }
        val arguments = when (actionType) {
            ACTION_NOTIFICATION -> mapOf(
                "title" to model.title.ifBlank { model.name.ifBlank { "Mason 提醒" } },
                "text" to model.text.ifBlank { request },
            )
            AutomationRunner.ACTION_MODEL_ARTIFACT -> mapOf(
                "prompt" to model.prompt.ifBlank { request },
                "file_name" to model.fileName.ifBlank { "automation-output.md" },
            ) + model.contextSource.takeIf { it == CONTEXT_CALENDAR }
                ?.let { mapOf("context_tool" to it) }
                .orEmpty()
            ACTION_LAUNCH_APP -> mapOf("package_name" to model.packageName)
            else -> emptyMap()
        }
        if (arguments.values.any(String::isBlank)) {
            return AutomationDraftResult(clarificationQuestion = "执行内容还不完整，请补充要提醒、生成或打开的具体内容。")
        }
        val network = model.network.lowercase().takeIf { it in SUPPORTED_NETWORKS }
            ?: AutomationScheduler.NETWORK_NONE
        val summary = buildSummary(triggerType, triggerValue, actionType, arguments)
        val actions = model.actions.takeIf(List<ModelAutomationStep>::isNotEmpty)
            ?.mapIndexed { index, step -> step.toAction(index) }
            ?: buildActions(request, actionType, arguments, model.contextSource)
        val warnings = capabilityInspector.inspect(triggerType, actions).map(AutomationCapabilityIssue::message)
        return AutomationDraftResult(
            draft = AutomationDraft(
                draftId = UUID.randomUUID().toString(),
                sourceRequest = request,
                name = model.name.trim().ifBlank { defaultName(actionType) }.take(60),
                triggerType = triggerType,
                triggerValue = triggerValue,
                actionType = actionType,
                arguments = arguments,
                constraints = MasonAutomationConstraints(
                    network = network,
                    requiresCharging = model.requiresCharging,
                    requiresBatteryNotLow = model.requiresBatteryNotLow,
                ),
                summary = summary,
                actions = actions,
                warnings = warnings,
            ),
        )
    }

    private fun parseModelDraft(content: String): ModelAutomationDraft? {
        val normalized = AutomationWorkflowLogic.normalizeStructuredOutput(content) ?: return null
        return runCatching { json.decodeFromString<ModelAutomationDraft>(normalized) }.getOrNull()
    }

        private fun normalizeTriggerValue(type: String, rawValue: String): String? = when (type) {
        TRIGGER_MANUAL -> ""
        AutomationScheduler.TRIGGER_INTERVAL -> rawValue.filter(Char::isDigit)
            .toLongOrNull()
            ?.takeIf { it >= AutomationScheduler.MIN_INTERVAL_MINUTES }
            ?.toString()
        AutomationScheduler.TRIGGER_DAILY -> normalizeTime(rawValue)
        AutomationScheduler.TRIGGER_WEEKDAYS -> {
            val weekdays = AutomationScheduler.selectedWeekdays(rawValue)
            val time = normalizeTime(AutomationScheduler.scheduledTime(rawValue))
            time?.let { AutomationScheduler.encodeWeekdays(weekdays, it) }
        }
        AutomationScheduler.TRIGGER_CHARGING -> ""
        AutomationScheduler.TRIGGER_WIFI,
        AutomationScheduler.TRIGGER_BLUETOOTH,
        AutomationScheduler.TRIGGER_NOTIFICATION -> rawValue.trim().takeIf(String::isNotBlank)
        AutomationScheduler.TRIGGER_LOCATION -> rawValue.trim().takeIf { value ->
            val parts = value.split(',')
            parts.size >= 2 && parts[0].toDoubleOrNull() != null && parts[1].toDoubleOrNull() != null
        }
        else -> null
    }

    private fun normalizeTime(value: String): String? {
        val match = Regex("(?:^|\\D)([01]?\\d|2[0-3])[:：]([0-5]\\d)(?:$|\\D)").find(value)
            ?: return null
        return "%02d:%02d".format(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    companion object {
        const val DRAFT_TOOL_NAME = "automation_draft"
        const val APPLY_TOOL_NAME = "automation_apply"
        const val TRIGGER_MANUAL = "manual"
        const val ACTION_NOTIFICATION = "notification"
        const val ACTION_LAUNCH_APP = "launch_app"
        const val OPERATION_CREATE = "create"
        const val OPERATION_UPDATE = "update"
        const val OPERATION_ENABLE = "enable"
        const val OPERATION_DISABLE = "disable"
        const val OPERATION_ARCHIVE = "archive"
        private const val DRAFT_MARKER_PREFIX = "<!-- mason-automation-draft "
        private const val APPLY_MARKER_PREFIX = "<!-- mason-automation-apply "
        private val SUPPORTED_ACTIONS = setOf(
            ACTION_NOTIFICATION,
            AutomationRunner.ACTION_MODEL_ARTIFACT,
            ACTION_LAUNCH_APP,
        )
        private val SUPPORTED_NETWORKS = setOf(
            AutomationScheduler.NETWORK_NONE,
            AutomationScheduler.NETWORK_CONNECTED,
            AutomationScheduler.NETWORK_UNMETERED,
        )
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
            isLenient = true
        }

        fun looksLikeAutomationRequest(text: String): Boolean {
            val normalized = text.lowercase()
            val explicit = listOf("自动化", "自动运行", "定时任务", "快捷指令").any(normalized::contains)
            val recurring = listOf("每天", "每日", "每周", "工作日", "每隔").any(normalized::contains)
            val action = listOf("提醒", "通知", "生成", "整理", "保存", "打开", "自动").any(normalized::contains)
            val timedReminder = Regex("(?:\\d{1,2}[:：点时]|早上|上午|中午|下午|晚上).*(?:提醒|通知)").containsMatchIn(normalized)
            return explicit || (recurring && action) || timedReminder
        }

        fun AutomationDraft.toMarker(): String = DRAFT_MARKER_PREFIX + json.encodeToString(this) + " -->"

        fun AutomationApplyResult.toMarker(): String = APPLY_MARKER_PREFIX + json.encodeToString(this) + " -->"

        fun extractAutomationDraftMarker(content: String): AutomationDraft? =
            extractMarker(content, DRAFT_MARKER_PREFIX)?.let { encoded ->
                runCatching { json.decodeFromString<AutomationDraft>(encoded) }.getOrNull()
            }

        fun extractAutomationApplyMarker(content: String): AutomationApplyResult? =
            extractMarker(content, APPLY_MARKER_PREFIX)?.let { encoded ->
                runCatching { json.decodeFromString<AutomationApplyResult>(encoded) }.getOrNull()
            }

        private fun extractMarker(content: String, prefix: String): String? = content
            .substringAfter(prefix, "")
            .substringBefore(" -->", "")
            .takeIf(String::isNotBlank)

        private fun draftPrompt(request: String): String = """
            把用户请求转换成一个 Mason 自动化草稿。只输出一个 JSON 对象，不要 Markdown，不要解释。
            支持 trigger_type: manual, interval, daily, weekdays。
            interval 的 trigger_value 是至少 15 的分钟数；daily 是 HH:mm；weekdays 是 1,2,3,4,5@HH:mm，1 代表周一。
            还支持 charging, wifi, bluetooth, notification, location 触发器。
            wifi/bluetooth/notification 的 trigger_value 是匹配关键字；location 是 纬度,经度,半径米。
            支持 action_type: notification, model_artifact, launch_app。后台任务不要使用 launch_app。
            model_artifact 会生成 Markdown 文件并自动发送完成通知。
            network 只能是 none, connected, unmetered。
            信息不足时 needs_clarification=true 并填写 question，不要猜时间。
            context_source 目前只能是空字符串或 calendar；用户明确要求整理日历时使用 calendar。
            prompt 只描述要生成的正文，不要包含设置定时任务、保存文件或提醒用户这些执行指令。
            JSON 必须包含：needs_clarification, question, name, trigger_type, trigger_value, action_type,
            title, text, prompt, file_name, context_source, package_name, network, requires_charging,
            requires_battery_not_low。
            复杂任务还必须包含 actions 数组。每项包含 id, title, type, arguments, input_key, output_key,
            condition_key, condition_operator, condition_value, continue_on_failure。
            actions type 支持 tool, skill, model_artifact, notification。tool 的 arguments 包含 tool_name；
            skill 包含 skill_id 和 prompt；后续步骤通过 input_key 引用前一步 output_key。
            如果/否则通过 condition_key、condition_operator 和 condition_value 表达；支持 not_empty、empty、
            equals、not_equals、contains、not_contains。不要生成循环，循环需求应拆成有限步骤或要求用户补充上限。

            用户请求：$request
        """.trimIndent()

        private fun inferCommonDraft(request: String): ModelAutomationDraft? {
            val compact = request.replace(" ", "")
            val intervalMatch = Regex("每隔([零一二两三四五六七八九十百\\d]+)(分钟|小时)").find(compact)
            val triggerType: String
            val triggerValue: String
            when {
                intervalMatch != null -> {
                    val amount = chineseNumber(intervalMatch.groupValues[1]) ?: return null
                    val minutes = if (intervalMatch.groupValues[2] == "小时") amount * 60 else amount
                    if (minutes < AutomationScheduler.MIN_INTERVAL_MINUTES) return null
                    triggerType = AutomationScheduler.TRIGGER_INTERVAL
                    triggerValue = minutes.toString()
                }
                listOf("工作日", "周一到周五", "周一至周五").any(compact::contains) -> {
                    val time = extractTime(compact) ?: return null
                    triggerType = AutomationScheduler.TRIGGER_WEEKDAYS
                    triggerValue = AutomationScheduler.encodeWeekdays((1..5).toSet(), time)
                }
                listOf("每天", "每日").any(compact::contains) -> {
                    triggerType = AutomationScheduler.TRIGGER_DAILY
                    triggerValue = extractTime(compact) ?: return null
                }
                compact.contains("充电时") || compact.contains("接上充电") -> {
                    triggerType = AutomationScheduler.TRIGGER_CHARGING
                    triggerValue = ""
                }
                compact.contains("wifi", ignoreCase = true) && compact.contains("连接") -> {
                    triggerType = AutomationScheduler.TRIGGER_WIFI
                    triggerValue = extractEventKeyword(compact, "wifi") ?: return null
                }
                compact.contains("蓝牙") && compact.contains("连接") -> {
                    triggerType = AutomationScheduler.TRIGGER_BLUETOOTH
                    triggerValue = extractEventKeyword(compact, "蓝牙") ?: return null
                }
                compact.contains("通知") && (compact.contains("收到") || compact.contains("出现")) -> {
                    triggerType = AutomationScheduler.TRIGGER_NOTIFICATION
                    triggerValue = Regex("(?:收到|出现)(.+?)通知").find(compact)?.groupValues?.getOrNull(1)
                        ?.takeIf(String::isNotBlank) ?: return null
                }
                listOf("自动化", "快捷指令").any(compact::contains) -> {
                    triggerType = TRIGGER_MANUAL
                    triggerValue = ""
                }
                else -> return null
            }

            val fileName = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.(?:md|txt|json|csv|html)", RegexOption.IGNORE_CASE)
                .find(request)
                ?.value
            val generatesFile = listOf("生成", "整理", "处理", "保存", "日报", "总结", "文件")
                .any(compact::contains) && (fileName != null || listOf("生成", "保存", "文件", "日报").any(compact::contains))
            val reminderOnly = listOf("提醒", "通知").any(compact::contains)
            val actionType = when {
                generatesFile -> AutomationRunner.ACTION_MODEL_ARTIFACT
                reminderOnly -> ACTION_NOTIFICATION
                else -> return null
            }
            val name = when {
                compact.contains("日报") -> "自动生成日报"
                compact.contains("总结") -> "自动生成今日总结"
                actionType == ACTION_NOTIFICATION -> "定时提醒"
                else -> "自动生成文件"
            }
            return ModelAutomationDraft(
                name = name,
                triggerType = triggerType,
                triggerValue = triggerValue,
                actionType = actionType,
                title = if (actionType == ACTION_NOTIFICATION) name else "",
                text = if (actionType == ACTION_NOTIFICATION) reminderText(request) else "",
                prompt = if (actionType == AutomationRunner.ACTION_MODEL_ARTIFACT) generationPrompt(compact) else "",
                fileName = fileName ?: if (compact.contains("日报") || compact.contains("总结")) {
                    "daily-summary.md"
                } else {
                    "automation-output.md"
                },
                contextSource = if (compact.contains("日历")) CONTEXT_CALENDAR else "",
                network = AutomationScheduler.NETWORK_NONE,
                requiresBatteryNotLow = actionType == AutomationRunner.ACTION_MODEL_ARTIFACT,
            )
        }

        private fun extractEventKeyword(text: String, marker: String): String? {
            val markerPattern = if (marker.equals("wifi", ignoreCase = true)) {
                "(?:WiFi|WIFI|wifi)"
            } else {
                Regex.escape(marker)
            }
            val before = Regex("连接(?:到)?[“\\\"]?(.+?)$markerPattern[”\\\"]?(?:时|后)")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
            val after = Regex("连接(?:到)?$markerPattern[“\\\"]?(.+?)[”\\\"]?(?:时|后)")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
            return before?.trim()?.takeIf(String::isNotBlank)
                ?: after?.trim()?.takeIf(String::isNotBlank)
        }

        private fun generationPrompt(request: String): String = when {
            request.contains("日历") -> "根据下方提供的今日日历事件，生成一份简洁的 Markdown 日报。只输出日报正文。"
            request.contains("日报") -> "生成一份简洁的 Markdown 今日日报；没有具体资料时输出可填写的日报结构。只输出正文。"
            request.contains("总结") -> "生成一份简洁的 Markdown 今日总结；没有具体资料时明确标注暂无可用资料。只输出正文。"
            else -> "按用户目标生成可直接保存的 Markdown 正文，不要讨论自动化设置。"
        }

        private fun reminderText(request: String): String {
            val candidates = listOf("提醒我", "通知我")
            return candidates.firstNotNullOfOrNull { marker ->
                request.substringAfter(marker, "")
                    .trim('：', ':', '，', ',', '。', ' ')
                    .takeIf(String::isNotBlank)
            } ?: request
        }

        private fun extractTime(text: String): String? {
            Regex("(?:^|\\D)([01]?\\d|2[0-3])[:：]([0-5]\\d)(?:$|\\D)").find(text)?.let { match ->
                return "%02d:%02d".format(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            }
            val match = Regex("(凌晨|早上|上午|中午|下午|晚上|夜里)?([零一二两三四五六七八九十\\d]+)[点时](半|[零一二两三四五六七八九十\\d]+分?)?")
                .find(text)
                ?: return null
            val period = match.groupValues[1]
            var hour = chineseNumber(match.groupValues[2]) ?: return null
            val minuteText = match.groupValues[3].removeSuffix("分")
            val minute = when {
                minuteText.isBlank() -> 0
                minuteText == "半" -> 30
                else -> chineseNumber(minuteText) ?: return null
            }
            if (period in setOf("中午", "下午", "晚上", "夜里") && hour < 12) hour += 12
            if (period == "凌晨" && hour == 12) hour = 0
            if (hour !in 0..23 || minute !in 0..59) return null
            return "%02d:%02d".format(hour, minute)
        }

        private fun chineseNumber(value: String): Int? {
            value.toIntOrNull()?.let { return it }
            val digits = mapOf('零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5,
                '六' to 6, '七' to 7, '八' to 8, '九' to 9)
            if (value == "十") return 10
            if ('十' in value) {
                val parts = value.split('十')
                val tens = parts.firstOrNull()?.firstOrNull()?.let(digits::get) ?: 1
                val ones = parts.getOrNull(1)?.firstOrNull()?.let(digits::get) ?: 0
                return tens * 10 + ones
            }
            return value.mapNotNull(digits::get).takeIf { it.size == value.length }
                ?.fold(0) { total, digit -> total * 10 + digit }
        }

        private fun triggerQuestion(type: String): String = when (type) {
            AutomationScheduler.TRIGGER_INTERVAL -> "每隔多少分钟运行一次？Android 后台任务最短为 15 分钟。"
            AutomationScheduler.TRIGGER_DAILY -> "每天几点运行？请给出明确时间，例如 22:00。"
            AutomationScheduler.TRIGGER_WEEKDAYS -> "每周哪几天、几点运行？例如周一到周五 09:00。"
            AutomationScheduler.TRIGGER_WIFI -> "连接到哪个 WiFi 时运行？请提供 WiFi 名称。"
            AutomationScheduler.TRIGGER_BLUETOOTH -> "连接到哪个蓝牙设备时运行？请提供设备名称。"
            AutomationScheduler.TRIGGER_NOTIFICATION -> "收到哪个 App 或包含什么文字的通知时运行？"
            AutomationScheduler.TRIGGER_LOCATION -> "到达哪里时运行？请提供明确地点。"
            else -> "什么时候运行这条自动化？"
        }

        private fun defaultName(actionType: String): String = when (actionType) {
            ACTION_NOTIFICATION -> "定时提醒"
            AutomationRunner.ACTION_MODEL_ARTIFACT -> "自动生成文件"
            else -> "Mason 自动化"
        }

        private fun buildActions(
            request: String,
            actionType: String,
            arguments: Map<String, String>,
            contextSource: String,
        ): List<MasonAutomationAction> {
            if (actionType != AutomationRunner.ACTION_MODEL_ARTIFACT) {
                return listOf(
                    MasonAutomationAction(
                        id = "action-1",
                        title = if (actionType == ACTION_NOTIFICATION) "发送提醒" else "打开 App",
                        type = actionType,
                        arguments = arguments,
                    ),
                )
            }
            return buildList {
                if (contextSource == CONTEXT_CALENDAR) {
                    add(
                        MasonAutomationAction(
                            id = "read-calendar",
                            title = "读取今日日历",
                            type = AutomationRunner.ACTION_TOOL,
                            arguments = mapOf("tool_name" to "calendar", "action" to "list"),
                            outputKey = "calendar_data",
                        ),
                    )
                }
                add(
                    MasonAutomationAction(
                        id = "generate-file",
                        title = "整理并生成文件",
                        type = AutomationRunner.ACTION_MODEL_ARTIFACT,
                        arguments = arguments - "context_tool",
                        inputKey = if (contextSource == CONTEXT_CALENDAR) "calendar_data" else "",
                        outputKey = "generation_result",
                        condition = if (contextSource == CONTEXT_CALENDAR) {
                            com.denggl2.mason.data.MasonAutomationCondition("calendar_data", "not_empty")
                        } else null,
                    ),
                )
                if (listOf("提醒", "通知").any(request::contains)) {
                    add(
                        MasonAutomationAction(
                            id = "notify-complete",
                            title = "发送完成提醒",
                            type = ACTION_NOTIFICATION,
                            arguments = mapOf(
                                "title" to "Mason 自动化已完成",
                                "text" to "已生成 {{artifact_name}}",
                            ),
                            condition = com.denggl2.mason.data.MasonAutomationCondition(
                                "artifact_path",
                                "not_empty",
                            ),
                        ),
                    )
                }
            }
        }

        private fun ModelAutomationStep.toAction(index: Int): MasonAutomationAction {
            val normalizedType = type.trim().lowercase()
            require(normalizedType in setOf(
                AutomationRunner.ACTION_TOOL,
                AutomationRunner.ACTION_SKILL,
                AutomationRunner.ACTION_MODEL_ARTIFACT,
                ACTION_NOTIFICATION,
            )) { "不支持的自动化步骤：$type" }
            return MasonAutomationAction(
                id = id.ifBlank { "action-${index + 1}" },
                title = title.ifBlank { AutomationWorkflowLogic.defaultTitle(normalizedType) },
                type = normalizedType,
                arguments = arguments,
                inputKey = inputKey,
                outputKey = outputKey,
                condition = conditionKey.takeIf(String::isNotBlank)?.let {
                    com.denggl2.mason.data.MasonAutomationCondition(
                        key = it,
                        operator = conditionOperator,
                        value = conditionValue,
                    )
                },
                continueOnFailure = continueOnFailure,
            )
        }

        private const val CONTEXT_CALENDAR = "calendar"

        private fun buildSummary(
            triggerType: String,
            triggerValue: String,
            actionType: String,
            arguments: Map<String, String>,
        ): String {
            val trigger = when (triggerType) {
                TRIGGER_MANUAL -> "手动运行"
                AutomationScheduler.TRIGGER_INTERVAL -> "每 $triggerValue 分钟运行"
                AutomationScheduler.TRIGGER_DAILY -> "每天 $triggerValue 运行"
                AutomationScheduler.TRIGGER_WEEKDAYS -> AutomationScheduler.describeWeekdays(triggerValue) + " 运行"
                else -> triggerType
            }
            val action = when (actionType) {
                ACTION_NOTIFICATION -> "提醒：${arguments["text"]}"
                AutomationRunner.ACTION_MODEL_ARTIFACT -> "生成文件：${arguments["file_name"]}"
                ACTION_LAUNCH_APP -> "打开 App：${arguments["package_name"]}"
                else -> actionType
            }
            return "$trigger；$action"
        }
    }
}
