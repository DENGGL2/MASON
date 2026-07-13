package com.denggl2.mason.data

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class LocalModelInstallState {
    NotInstalled,
    Installed,
    FileMissing,
    DeviceMayBeUnsupported,
}

data class LocalModelFileState(
    val modelId: String,
    val state: LocalModelInstallState,
    val path: String? = null,
    val fileName: String? = null,
    val sourceFileName: String? = null,
    val sizeBytes: Long = 0L,
    val recommendedRamGb: Int = 0,
    val availableRamGb: Int = 0,
) {
    val installed: Boolean
        get() = state == LocalModelInstallState.Installed ||
            state == LocalModelInstallState.DeviceMayBeUnsupported
}

@Singleton
class LocalModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun states(models: List<LocalModelPreset>): List<LocalModelFileState> =
        models.map(::stateFor)

    fun stateFor(model: LocalModelPreset): LocalModelFileState {
        val file = modelFile(model.id)
        val availableRamGb = availableRamGb()
        val base = LocalModelFileState(
            modelId = model.id,
            state = LocalModelInstallState.NotInstalled,
            path = file.absolutePath,
            fileName = file.name,
            sizeBytes = 0L,
            recommendedRamGb = model.recommendedRamGb,
            availableRamGb = availableRamGb,
        )
        if (!file.exists()) return base
        if (!file.isFile || file.length() <= 0L) {
            return base.copy(state = LocalModelInstallState.FileMissing)
        }
        val state = if (availableRamGb in 1 until model.recommendedRamGb) {
            LocalModelInstallState.DeviceMayBeUnsupported
        } else {
            LocalModelInstallState.Installed
        }
        return base.copy(
            state = state,
            sizeBytes = file.length(),
        )
    }

    suspend fun importModel(model: LocalModelPreset, uri: Uri): LocalModelFileState =
        withContext(Dispatchers.IO) {
            val sourceName = displayName(uri)
            val target = modelFile(model.id, sourceName)
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取模型文件" }
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            stateFor(model).copy(sourceFileName = sourceName)
        }

    fun readyModelPath(modelId: String): String? {
        val model = LocalModelCatalog.get(modelId) ?: return null
        val state = stateFor(model)
        return if (state.installed) state.path else null
    }

    fun inferenceCacheDir(): File =
        File(context.cacheDir, "litertlm").also { it.mkdirs() }

    fun displayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
    }

    private fun modelFile(modelId: String, sourceFileName: String? = null): File {
        val dir = File(context.filesDir, "local_models")
        val preferred = File(dir, "$modelId.litertlm")
        val legacy = File(dir, "$modelId.task")
        val extension = sourceFileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
        return when {
            extension == "task" -> legacy
            !sourceFileName.isNullOrBlank() -> preferred
            preferred.exists() -> preferred
            legacy.exists() -> legacy
            else -> preferred
        }
    }

    private fun availableRamGb(): Int {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 0
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val gb = info.totalMem / (1024L * 1024L * 1024L)
        return gb.toInt().coerceAtLeast(1)
    }
}
