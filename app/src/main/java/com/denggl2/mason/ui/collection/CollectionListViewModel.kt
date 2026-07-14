package com.denggl2.mason.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.data.SkillAutomationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SkillManagementUiState(
    val working: Boolean = false,
    val revision: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class CollectionListViewModel @Inject constructor(
    private val skillStore: SkillAutomationStore,
) : ViewModel() {
    private val _skillState = MutableStateFlow(SkillManagementUiState())
    val skillState: StateFlow<SkillManagementUiState> = _skillState.asStateFlow()

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
}
