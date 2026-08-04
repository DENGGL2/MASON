package com.denggl2.mason.data

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
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
    val extension: String = "",
    val formatSupported: Boolean = false,
    val formatWarning: String? = null,
    val sizeBytes: Long = 0L,
    val recommendedRamGb: Int = 0,
    val availableRamGb: Int = 0,
) {
    val installed: Boolean
        get() = state == LocalModelInstallState.Installed ||
            state == LocalModelInstallState.DeviceMayBeUnsupported

    val diagnosticSummary: String
        get() = buildString {
            append(fileName ?: modelId)
            if (sizeBytes > 0L) append(" · ").append(formatBytes(sizeBytes))
            if (extension.isNotBlank()) append(" · .").append(extension)
            append(" · RAM ").append(availableRamGb).append("GB")
            if (recommendedRamGb > 0) append("/建议 ").append(recommendedRamGb).append("GB")
            formatWarning?.let { append(" · ").append(it) }
        }
}

@Singleton
class LocalModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun states(models: List<LocalModelPreset>): List<LocalModelFileState> =
        models.map(::stateFor)

    fun stateFor(model: LocalModelPreset): LocalModelFileState {
        val file = modelFile(model)
        val availableRamGb = availableRamGb()
        val base = LocalModelFileState(
            modelId = model.id,
            state = LocalModelInstallState.NotInstalled,
            path = file.absolutePath,
            fileName = file.name,
            extension = file.extension.lowercase(),
            formatSupported = file.hasSupportedExtension(model),
            formatWarning = file.formatWarning(model),
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
            val target = modelFile(model, sourceName)
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

    fun diagnostics(model: LocalModelPreset): String =
        stateFor(model).diagnosticSummary

    fun inferenceCacheDir(): File =
        File(context.cacheDir, "litertlm").also { it.mkdirs() }

    internal fun modelFileForDownload(modelId: String): File =
        modelFile(requireNotNull(LocalModelCatalog.get(modelId)))

    internal fun partialFileForDownload(modelId: String): File =
        File(modelFileForDownload(modelId).absolutePath + ".part")

    fun partialDownloadBytes(modelId: String): Long =
        partialFileForDownload(modelId).takeIf { it.isFile }?.length() ?: 0L

    fun availableStorageBytes(): Long =
        StatFs(context.filesDir.absolutePath).availableBytes

    suspend fun deleteModel(model: LocalModelPreset) = withContext(Dispatchers.IO) {
        val target = modelFile(model)
        val partial = partialFileForDownload(model.id)
        if (target.exists()) check(target.delete()) { "无法删除模型文件" }
        if (partial.exists()) check(partial.delete()) { "无法删除未完成的下载" }
    }

    suspend fun resetPartialDownload(modelId: String) = withContext(Dispatchers.IO) {
        val partial = partialFileForDownload(modelId)
        if (partial.exists()) check(partial.delete()) { "无法清理已取消的下载" }
    }

    fun displayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
    }

    private fun modelFile(model: LocalModelPreset, sourceFileName: String? = null): File {
        val dir = File(context.filesDir, "local_models")
        val preferred = File(dir, "${model.id}.${model.fileExtension}")
        val legacy = File(dir, "${model.id}.task")
        val extension = sourceFileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
        return when {
            model.runtime == LocalModelCatalog.RUNTIME_LITERT && extension == "task" -> legacy
            !sourceFileName.isNullOrBlank() -> preferred
            preferred.exists() -> preferred
            model.runtime == LocalModelCatalog.RUNTIME_LITERT && legacy.exists() -> legacy
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

    private fun File.hasSupportedExtension(model: LocalModelPreset): Boolean =
        extension.lowercase() == model.fileExtension ||
            (model.runtime == LocalModelCatalog.RUNTIME_LITERT && extension.lowercase() == "task")

    private fun File.formatWarning(model: LocalModelPreset): String? {
        val ext = extension.lowercase()
        return when {
            ext.isBlank() -> "文件没有扩展名，可能无法判断格式"
            !hasSupportedExtension(model) -> "格式可能不兼容 ${model.runtime}"
            ext == "task" -> "旧 .task 格式，仅兼容部分运行时"
            else -> null
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 0.1) return "%.1f GB".format(java.util.Locale.US, gb)
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(java.util.Locale.US, mb)
}
