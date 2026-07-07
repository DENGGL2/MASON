package com.denggl2.mason.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.crashguard.data.CrashDao
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configDataStore: ApiConfigDataStore,
    private val syncManager: SyncManager,
    private val crashDao: CrashDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val config: StateFlow<ApiConfig> = configDataStore.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent = _toastEvent.asSharedFlow()

    val appVersion: String by lazy {
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "Unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    fun save(config: ApiConfig) {
        viewModelScope.launch {
            configDataStore.updateConfig(config)
        }
    }

    fun exportConversations() {
        viewModelScope.launch {
            try {
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "mason"
                )
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val timestamp = System.currentTimeMillis()
                val file = File(downloadDir, "mason_backup_$timestamp.json")
                val success = syncManager.exportToFile(file)
                if (success) {
                    _toastEvent.emit("导出成功：${file.absolutePath}")
                } else {
                    _toastEvent.emit("导出失败")
                }
            } catch (e: Exception) {
                _toastEvent.emit("导出失败：${e.message}")
            }
        }
    }

    fun importConversations(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = syncManager.importFromUri(uri)
                _toastEvent.emit("导入成功：${count} 个对话")
            } catch (e: Exception) {
                _toastEvent.emit("导入失败：${e.message}")
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            try {
                syncManager.clearAll()
                _toastEvent.emit("已清除所有对话")
            } catch (e: Exception) {
                _toastEvent.emit("清除失败：${e.message}")
            }
        }
    }

    fun clearCrashLogs() {
        viewModelScope.launch {
            try {
                crashDao.clearAll()
                _toastEvent.emit("已清除崩溃日志")
            } catch (e: Exception) {
                _toastEvent.emit("清除失败：${e.message}")
            }
        }
    }
}
