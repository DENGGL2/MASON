package com.denggl2.mason.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.automation.AutomationRunner
import com.denggl2.mason.automation.AutomationScheduler
import com.denggl2.mason.automation.AutomationCapabilityInspector
import com.denggl2.mason.data.AutomationPreferences
import com.denggl2.mason.data.AutomationPreferencesDataStore
import com.denggl2.mason.data.SkillAutomationStore
import com.denggl2.mason.data.MasonAutomationAction
import com.denggl2.mason.data.MasonAutomationConstraints
import com.denggl2.mason.data.MasonAutomationRunLog
import com.denggl2.mason.data.MasonAutomationSpec
import com.denggl2.mason.data.MasonAutomationTrigger
import com.denggl2.mason.automation.AutomationWorkflowLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class SkillManagementUiState(
    val working: Boolean = false,
    val revision: Int = 0,
    val message: String? = null,
)

data class AutomationManagementUiState(
    val working: Boolean = false,
    val revision: Int = 0,
    val message: String? = null,
    val logTitle: String? = null,
    val logs: List<MasonAutomationRunLog> = emptyList(),
)

@HiltViewModel
class CollectionListViewModel @Inject constructor(
    private val skillStore: SkillAutomationStore,
    private val automationRunner: AutomationRunner,
    private val automationScheduler: AutomationScheduler,
    private val capabilityInspector: AutomationCapabilityInspector,
    automationPreferencesStore: AutomationPreferencesDataStore,
) : ViewModel() {
    private val _skillState = MutableStateFlow(SkillManagementUiState())
    val skillState: StateFlow<SkillManagementUiState> = _skillState.asStateFlow()
    private val _automationState = MutableStateFlow(AutomationManagementUiState())
    val automationState: StateFlow<AutomationManagementUiState> = _automationState.asStateFlow()
    private val _automationCapabilityIssues = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val automationCapabilityIssues: StateFlow<Map<String, List<String>>> =
        _automationCapabilityIssues.asStateFlow()
    val automationPreferences: StateFlow<AutomationPreferences> = automationPreferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutomationPreferences())

    fun refreshAutomationCapabilities() {
        viewModelScope.launch {
            _automationCapabilityIssues.value = skillStore.listAutomations().associate { spec ->
                spec.id to capabilityInspector.inspect(spec.trigger.type, spec.actions)
                    .map { it.message }
            }
        }
    }

    fun installSkillFromGitHub(url: String) {
        if (_skillState.value.working) return
        viewModelScope.launch {
            _skillState.value = _skillState.value.copy(working = true, message = null)
            runCatching { skillStore.installFromGitHub(url) }
                .onSuccess { skill ->
                    _skillState.value = SkillManagementUiState(
                        revision = _skillState.value.revision + 1,
                        message = "已安装 ${skill.manifest.name}",
                    )
                }
                .onFailure { error ->
                    _skillState.value = _skillState.value.copy(
                        working = false,
                        message = error.message ?: "Skill 安装失败",
                    )
                }
        }
    }

    fun setSkillEnabled(path: String, enabled: Boolean) {
        if (_skillState.value.working) return
        viewModelScope.launch {
            _skillState.value = _skillState.value.copy(working = true, message = null)
            runCatching { skillStore.setSkillEnabledAtPath(path, enabled) }
                .onSuccess { manifest ->
                    _skillState.value = SkillManagementUiState(
                        revision = _skillState.value.revision + 1,
                        message = if (manifest == null) {
                            "这个 Skill 不能由 Mason 管理"
                        } else if (enabled) {
                            "已启用 ${manifest.name}"
                        } else {
                            "已停用 ${manifest.name}"
                        },
                    )
                }
                .onFailure { error ->
                    _skillState.value = _skillState.value.copy(
                        working = false,
                        message = error.message ?: "Skill 状态更新失败",
                    )
                }
        }
    }

    fun updateSkill(skillId: String) {
        if (_skillState.value.working) return
        viewModelScope.launch {
            _skillState.value = _skillState.value.copy(working = true, message = null)
            runCatching { skillStore.updateFromGitHub(skillId) }
                .onSuccess { skill ->
                    _skillState.value = SkillManagementUiState(
                        revision = _skillState.value.revision + 1,
                        message = "已更新 ${skill.manifest.name} ${skill.manifest.version}",
                    )
                }
                .onFailure { error ->
                    _skillState.value = _skillState.value.copy(
                        working = false,
                        message = error.message ?: "Skill 更新失败",
                    )
                }
        }
    }

    fun archiveSkill(skillId: String) {
        if (_skillState.value.working) return
        viewModelScope.launch {
            _skillState.value = _skillState.value.copy(working = true, message = null)
            runCatching { skillStore.archiveSkill(skillId) }
                .onSuccess {
                    _skillState.value = SkillManagementUiState(
                        revision = _skillState.value.revision + 1,
                        message = "Skill 已卸载，可通过重新安装恢复",
                    )
                }
                .onFailure { error ->
                    _skillState.value = _skillState.value.copy(
                        working = false,
                        message = error.message ?: "Skill 卸载失败",
                    )
                }
        }
    }

    fun consumeMessage() {
        _skillState.value = _skillState.value.copy(message = null)
    }

    fun createAutomation(
        name: String,
        actionType: String,
        arguments: Map<String, String>,
        triggerType: String,
        triggerValue: String,
        constraints: MasonAutomationConstraints,
    ) {
        if (_automationState.value.working) return
        viewModelScope.launch {
            _automationState.value = _automationState.value.copy(working = true, message = null)
            val spec = MasonAutomationSpec(
                id = "automation-${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                description = automationDescription(actionType, arguments, triggerType, triggerValue, constraints),
                enabled = true,
                trigger = MasonAutomationTrigger(
                    type = triggerType,
                    value = triggerValue,
                ),
                actions = listOf(MasonAutomationAction(type = actionType, arguments = arguments)),
                constraints = constraints,
            )
            runCatching { skillStore.saveAutomation(spec) }
                .onSuccess { saved ->
                    automationScheduler.sync(saved)
                    refreshAutomationCapabilities()
                    _automationState.value = AutomationManagementUiState(
                        revision = _automationState.value.revision + 1,
                        message = "已创建 ${saved.name}",
                    )
                }
                .onFailure { error ->
                    _automationState.value = _automationState.value.copy(
                        working = false,
                        message = error.message ?: "自动化创建失败",
                    )
                }
        }
    }

    fun updateAutomation(
        automationId: String,
        name: String,
        actionType: String,
        arguments: Map<String, String>,
        triggerType: String,
        triggerValue: String,
        constraints: MasonAutomationConstraints,
    ) {
        if (_automationState.value.working) return
        viewModelScope.launch {
            _automationState.value = _automationState.value.copy(working = true, message = null)
            runCatching {
                val current = checkNotNull(skillStore.automation(automationId)) { "没有找到自动化" }
                val updated = current.copy(
                    name = name.trim(),
                    description = automationDescription(actionType, arguments, triggerType, triggerValue, constraints),
                    trigger = MasonAutomationTrigger(type = triggerType, value = triggerValue),
                    actions = listOf(MasonAutomationAction(type = actionType, arguments = arguments)),
                    constraints = constraints,
                )
                skillStore.saveAutomation(updated).also { automationScheduler.sync(it) }
            }.onSuccess { saved ->
                refreshAutomationCapabilities()
                _automationState.value = AutomationManagementUiState(
                    revision = _automationState.value.revision + 1,
                    message = "已更新 ${saved.name}",
                )
            }.onFailure { error ->
                _automationState.value = _automationState.value.copy(
                    working = false,
                    message = error.message ?: "自动化更新失败",
                )
            }
        }
    }

    fun updateAutomationWorkflow(
        automationId: String,
        name: String,
        trigger: MasonAutomationTrigger,
        actions: List<MasonAutomationAction>,
    ) {
        if (_automationState.value.working) return
        viewModelScope.launch {
            _automationState.value = _automationState.value.copy(working = true, message = null)
            runCatching {
                require(actions.isNotEmpty()) { "自动化至少需要一个步骤" }
                val current = checkNotNull(skillStore.automation(automationId)) { "没有找到自动化" }
                val normalized = AutomationWorkflowLogic.normalizedActions(actions)
                val updated = current.copy(
                    name = name.trim(),
                    description = "${normalized.size} 个步骤 · ${trigger.type}",
                    trigger = trigger,
                    actions = normalized,
                )
                skillStore.saveAutomation(updated).also { automationScheduler.sync(it) }
            }.onSuccess { saved ->
                refreshAutomationCapabilities()
                _automationState.value = AutomationManagementUiState(
                    revision = _automationState.value.revision + 1,
                    message = "已更新 ${saved.name} 的 ${saved.actions.size} 个步骤",
                )
            }.onFailure { error ->
                _automationState.value = _automationState.value.copy(
                    working = false,
                    message = error.message ?: "自动化更新失败",
                )
            }
        }
    }

    fun testAutomationThroughStep(automationId: String, actionId: String) {
        if (_automationState.value.working) return
        viewModelScope.launch {
            _automationState.value = _automationState.value.copy(working = true, message = null)
            runCatching { automationRunner.runThroughStep(automationId, actionId) }
                .onSuccess { (_, log) ->
                    _automationState.value = AutomationManagementUiState(
                        revision = _automationState.value.revision + 1,
                        message = log.message,
                    )
                }
                .onFailure { error ->
                    _automationState.value = _automationState.value.copy(
                        working = false,
                        message = error.message ?: "步骤测试失败",
                    )
                }
        }
    }

    fun setAutomationEnabled(automationId: String, enabled: Boolean) {
        if (_automationState.value.working) return
        viewModelScope.launch {
            _automationState.value = _automationState.value.copy(working = true, message = null)
            runCatching { skillStore.setAutomationEnabled(automationId, enabled) }
                .onSuccess { spec ->
                    if (spec != null) automationScheduler.sync(spec)
                    refreshAutomationCapabilities()
                    _automationState.value = AutomationManagementUiState(
                        revision = _automationState.value.revision + 1,
                        message = when {
                            spec == null -> "没有找到自动化"
                            enabled -> "已启用 ${spec.name}"
                            else -> "已停用 ${spec.name}"
                        },
                    )
                }
                .onFailure { error ->
                    _automationState.value = _automationState.value.copy(
                        working = false,
                        message = error.message ?: "自动化状态更新失败",
                    )
                }
        }
    }

    fun runAutomation(automationId: String) {
        if (_automationState.value.working) return
        viewModelScope.launch {
            _automationState.value = _automationState.value.copy(working = true, message = null)
            runCatching {
                automationRunner.run(automationId, AutomationRunner.SOURCE_MANUAL)
            }.onSuccess { (spec, log) ->
                _automationState.value = AutomationManagementUiState(
                    revision = _automationState.value.revision + 1,
                    message = if (log.status == "success") {
                        "${spec.name} 运行成功"
                    } else {
                        "${spec.name} 运行失败：${log.message}"
                    },
                )
            }.onFailure { error ->
                val message = error.message ?: "自动化运行失败"
                _automationState.value = _automationState.value.copy(
                    working = false,
                    revision = _automationState.value.revision + 1,
                    message = message,
                )
            }
        }
    }

    fun showAutomationLogs(automationId: String, title: String) {
        viewModelScope.launch {
            val logs = skillStore.readAutomationLogs(automationId).sortedByDescending { it.ranAt }
            _automationState.value = _automationState.value.copy(logTitle = title, logs = logs)
        }
    }

    fun closeAutomationLogs() {
        _automationState.value = _automationState.value.copy(logTitle = null, logs = emptyList())
    }

    fun consumeAutomationMessage() {
        _automationState.value = _automationState.value.copy(message = null)
    }

    private fun automationDescription(
        type: String,
        arguments: Map<String, String>,
        triggerType: String,
        triggerValue: String,
        constraints: MasonAutomationConstraints,
    ): String {
        val action = when (type) {
            "notification" -> "发送通知：${arguments["title"].orEmpty()}"
            AutomationRunner.ACTION_MODEL_ARTIFACT -> "AI 产出：${arguments["file_name"].orEmpty()}"
            "launch_app" -> "打开 App：${arguments["package_name"].orEmpty()}"
            else -> "自动化"
        }
        val trigger = when (triggerType) {
            AutomationScheduler.TRIGGER_INTERVAL -> "每 ${triggerValue.ifBlank { AutomationScheduler.MIN_INTERVAL_MINUTES }} 分钟"
            AutomationScheduler.TRIGGER_DAILY -> "每天 $triggerValue"
            AutomationScheduler.TRIGGER_WEEKDAYS -> AutomationScheduler.describeWeekdays(triggerValue)
            AutomationScheduler.TRIGGER_CHARGING -> "接上充电器时"
            AutomationScheduler.TRIGGER_WIFI -> "连接 WiFi：$triggerValue"
            AutomationScheduler.TRIGGER_BLUETOOTH -> "连接蓝牙：$triggerValue"
            AutomationScheduler.TRIGGER_NOTIFICATION -> "收到通知：$triggerValue"
            AutomationScheduler.TRIGGER_LOCATION -> "进入指定位置"
            else -> "手动"
        }
        val conditions = buildList {
            when (constraints.network) {
                AutomationScheduler.NETWORK_CONNECTED -> add("需要联网")
                AutomationScheduler.NETWORK_UNMETERED -> add("非计费网络")
            }
            if (constraints.requiresCharging) add("充电时")
            if (constraints.requiresBatteryNotLow) add("电量充足")
        }
        return (listOf(action, trigger) + conditions).joinToString(" · ")
    }
}
