package com.denggl2.mason.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configDataStore: ApiConfigDataStore,
) : ViewModel() {

    val config: StateFlow<ApiConfig> = configDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    fun save(config: ApiConfig) {
        viewModelScope.launch {
            configDataStore.updateConfig(config)
        }
    }
}
