package com.denggl2.mason.tool

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Singleton
class StorageTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "storage"
    override val description = "查询设备存储空间信息：内部存储和外部存储（SD卡）的总空间、可用空间、已用空间和使用百分比"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        return try {
            val storages = mutableListOf<Map<String, String>>()

            // Internal storage
            val internalPath = Environment.getDataDirectory()
            val internalStat = StatFs(internalPath.absolutePath)
            val internalTotal = internalStat.totalBytes
            val internalAvailable = internalStat.availableBytes
            val internalUsed = internalTotal - internalAvailable
            val internalPercent = if (internalTotal > 0) {
                (internalUsed * 100 / internalTotal).toInt()
            } else {
                0
            }

            storages.add(
                mapOf(
                    "type" to "内部存储",
                    "path" to internalPath.absolutePath,
                    "filesystem" to getFilesystemType(internalPath),
                    "total_bytes" to internalTotal.toString(),
                    "available_bytes" to internalAvailable.toString(),
                    "used_bytes" to internalUsed.toString(),
                    "usage_percent" to "$internalPercent%",
                )
            )

            // External storage (SD card or emulated)
            val externalDirs = context.getExternalFilesDirs(null)
            val seenPaths = mutableSetOf(internalPath.absolutePath)

            for (dir in externalDirs) {
                if (dir == null) continue
                // Get the root of the external storage
                val rootPath = getExternalStorageRoot(dir.absolutePath)
                if (rootPath == null || rootPath in seenPaths) continue
                seenPaths.add(rootPath)

                try {
                    val externalStat = StatFs(rootPath)
                    val externalTotal = externalStat.totalBytes
                    val externalAvailable = externalStat.availableBytes
                    val externalUsed = externalTotal - externalAvailable
                    val externalPercent = if (externalTotal > 0) {
                        (externalUsed * 100 / externalTotal).toInt()
                    } else {
                        0
                    }

                    val isRemovable = isRemovableStorage(rootPath)
                    val label = if (isRemovable) "外部存储 (SD卡)" else "外部存储"

                    storages.add(
                        mapOf(
                            "type" to label,
                            "path" to rootPath,
                            "filesystem" to getFilesystemType(File(rootPath)),
                            "total_bytes" to externalTotal.toString(),
                            "available_bytes" to externalAvailable.toString(),
                            "used_bytes" to externalUsed.toString(),
                            "usage_percent" to "$externalPercent%",
                        )
                    )
                } catch (_: Exception) {
                    // Skip inaccessible storage
                }
            }

            // Build summary
            val summary = buildString {
                appendLine("设备存储空间信息:")
                appendLine()
                storages.forEach { storage ->
                    val totalGB = (storage["total_bytes"]?.toLongOrNull() ?: 0) / (1024.0 * 1024 * 1024)
                    val availGB = (storage["available_bytes"]?.toLongOrNull() ?: 0) / (1024.0 * 1024 * 1024)
                    val usedGB = (storage["used_bytes"]?.toLongOrNull() ?: 0) / (1024.0 * 1024 * 1024)

                    appendLine("${storage["type"]}:")
                    appendLine("  路径: ${storage["path"]}")
                    appendLine("  文件系统: ${storage["filesystem"]}")
                    appendLine("  总空间: %.2f GB (%s 字节)".format(totalGB, storage["total_bytes"]))
                    appendLine("  已用空间: %.2f GB (%s 字节)".format(usedGB, storage["used_bytes"]))
                    appendLine("  可用空间: %.2f GB (%s 字节)".format(availGB, storage["available_bytes"]))
                    appendLine("  使用率: ${storage["usage_percent"]}")
                    appendLine()
                }
            }

            // Flatten data for ToolResult
            val resultData = mutableMapOf<String, String>()
            storages.forEachIndexed { index, storage ->
                val prefix = "storage_${index}_"
                storage.forEach { (key, value) ->
                    resultData["${prefix}$key"] = value
                }
            }
            resultData["storage_count"] = storages.size.toString()
            resultData["summary"] = summary

            ToolResult(success = true, data = resultData)
        } catch (e: Exception) {
            ToolResult(success = false, error = "查询存储空间失败: ${e.message}")
        }
    }

    private fun getExternalStorageRoot(path: String): String? {
        val file = File(path)
        var current: File? = file
        val dataPath = Environment.getDataDirectory().absolutePath

        while (current != null && current.absolutePath != "/") {
            if (current.absolutePath == "/storage/emulated" ||
                current.absolutePath.startsWith("/storage/") && !current.absolutePath.startsWith("/storage/emulated/")
            ) {
                return current.absolutePath
            }
            // Also check if this is the mount point root
            if (current.absolutePath.startsWith("/storage/") &&
                current.absolutePath != "/storage/emulated/0" &&
                current.parentFile?.absolutePath == "/storage"
            ) {
                return current.absolutePath
            }
            current = current.parentFile
        }

        // Fallback: extract from common pattern /storage/XXXX/Android/data/...
        val pattern = Regex("^(/storage/[^/]+)")
        val match = pattern.find(path)
        return match?.groupValues?.get(1)
    }

    private fun isRemovableStorage(path: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                sm?.let {
                    val storageVolumes = it.storageVolumes
                    for (volume in storageVolumes) {
                        val volumePath = getVolumePath(volume)
                        if (volumePath != null && path.startsWith(volumePath)) {
                            return !volume.isPrimary && volume.isRemovable
                        }
                    }
                }
            }
            // Fallback heuristic: non-emulated /storage/ paths are likely removable
            path.startsWith("/storage/") && !path.contains("emulated")
        } catch (_: Exception) {
            false
        }
    }

    private fun getVolumePath(volume: StorageVolume): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                volume.directory?.absolutePath
            } else {
                @Suppress("DEPRECATION")
                val field = StorageVolume::class.java.getDeclaredField("mPath")
                field.isAccessible = true
                field.get(volume) as? String
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getFilesystemType(file: File): String {
        return try {
            // Use StatFs to infer filesystem type (limited on Android)
            // Common Android filesystems: ext4, f2fs, sdcardfs, fuse, vfat/exfat
            val path = file.absolutePath
            when {
                path.contains("emulated") -> "sdcardfs/fuse"
                path.startsWith("/data") -> "ext4/f2fs"
                path.startsWith("/storage/") && !path.contains("emulated") -> "vfat/exfat"
                path.startsWith("/system") -> "ext4"
                path.startsWith("/cache") -> "ext4"
                else -> "未知"
            }
        } catch (_: Exception) {
            "未知"
        }
    }
}
