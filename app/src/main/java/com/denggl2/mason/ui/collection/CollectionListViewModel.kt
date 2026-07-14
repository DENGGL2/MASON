package com.denggl2.mason.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.data.SkillAutomationStore
import com.denggl2.mason.data.MasonAutomationAction
import com.denggl2.mason.data.MasonAutomationRunLog
import com.denggl2.mason.data.MasonAutomationSpec
import com.denggl2.mason.data.MasonAutomationTrigger
import com.denggl2.mason.tool.ToolExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val toolExecutor: ToolExecutor,
) : ViewModel() {
    private val _skillState = MutableStateFlow(SkillManagementUiState())
    val skillState: StateFlow<SkillManagementUiState> = _skillState.asStateFlow()
    private val _automationState = MutableStateFlow(AutomationManagementUiState())
    val automationState: StateFlow<AutomationManagementUiState> = _automationState.asStateFlow()

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

    fun consumeMessage() {
        _skillState.value = _skillState.value.copy(message = null)
    }

    fun createAutomation(
        name: String,
        actionType: String,
        arguments: Map<String, String>,
    ) {
        if (_automationState.value.working) return
        viewModelScope.launch {
            _automationState.value = _automationState.value.copy(working = true, message = null)
            val spec = MasonAutomationSpec(
                id = "automation-${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                description = automationDescription(actionType, arguments),
                enabled = true,
                trigger = MasonAutomationTrigger(type = "manual"),
                actions = listOf(MasonAutomationAction(type = actionType, arguments = arguments)),
            )
            runCatching { skillStore.saveAutomation(spec) }
                .onSuccess { saved ->
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

    fun setAutomationEnabled(automationId: String, enabled: Boolean) {
        if (_automationState.value.working) return
        viewModelScope.launch {
            _automationState.value = _automationState.value.copy(working = true, message = null)
            runCatching { skillStore.setAutomationEnabled(automationId, enabled) }
                .onSuccess { spec ->
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
            val outcome = runCatching {
                val spec = checkNotNull(skillStore.automation(automationId)) { "没有找到自动化" }
                var failure: String? = null
                for (action in spec.actions) {
                    val toolName = when (action.type) {
                        "notification" -> "notification"
                        "launch_app" -> "launch_app"
                        else -> error("不支持的自动化动作：${action.type}")
                    }
                    val result = toolExecutor.execute(toolName, action.arguments)
                    if (!result.success) {
                        failure = result.error ?: "$toolName 执行失败"
                        break
                    }
                }
                val log = MasonAutomationRunLog(
                    automationId = automationId,
                    status = if (failure == null) "success" else "failed",
                    message = failure ?: "${spec.actions.size} 个动作执行完成",
                )
                skillStore.appendAutomationLog(log)
                spec to log
            }
            outcome.onSuccess { (spec, log) ->
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
                runCatching {
                    skillStore.appendAutomationLog(
                        MasonAutomationRunLog(
                            automationId = automationId,
                            status = "failed",
                            message = message,
                        ),
                    )
                }
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

    private fun automationDescription(type: String, arguments: Map<String, String>): String = when (type) {
        "notification" -> "发送通知：${arguments["title"].orEmpty()}"
        "launch_app" -> "打开 App：${arguments["package_name"].orEmpty()}"
        else -> "手动自动化"
    }
}
