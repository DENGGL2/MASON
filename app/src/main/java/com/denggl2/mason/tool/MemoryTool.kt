package com.denggl2.mason.tool

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "get_memory_info"
    override val description = "获取内存和存储信息：总RAM、可用RAM、内部存储总量/可用、SD卡状态"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val info = mutableMapOf<String, String>()

        // RAM
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        info["total_ram_gb"] = "%.1f GB".format(memInfo.totalMem / (1024.0 * 1024 * 1024))
        info["available_ram_gb"] = "%.1f GB".format(memInfo.availMem / (1024.0 * 1024 * 1024))
        info["low_memory"] = if (memInfo.lowMemory) "是" else "否"

        // 内部存储
        val dataPath = Environment.getDataDirectory()
        val dataStat = StatFs(dataPath.path)
        val blockSize = dataStat.blockSizeLong
        val totalBlocks = dataStat.blockCountLong
        val availBlocks = dataStat.availableBlocksLong

        info["internal_total_gb"] = "%.1f GB".format(totalBlocks * blockSize / (1024.0 * 1024 * 1024))
        info["internal_avail_gb"] = "%.1f GB".format(availBlocks * blockSize / (1024.0 * 1024 * 1024))

        // SD 卡（如果存在）
        val externalDirs = context.getExternalFilesDirs(null)
        if (externalDirs.size > 1 && externalDirs[1] != null) {
            val sdPath = externalDirs[1]!!.path.substringBefore("/Android")
            try {
                val sdStat = StatFs(sdPath)
                val sdBlockSize = sdStat.blockSizeLong
                val sdTotal = sdStat.blockCountLong
                val sdAvail = sdStat.availableBlocksLong
                info["sd_total_gb"] = "%.1f GB".format(sdTotal * sdBlockSize / (1024.0 * 1024 * 1024))
                info["sd_avail_gb"] = "%.1f GB".format(sdAvail * sdBlockSize / (1024.0 * 1024 * 1024))
            } catch (_: Exception) {
                info["sd_card"] = "不可用"
            }
        } else {
            info["sd_card"] = "未插入"
        }

        return ToolResult(success = true, data = info)
    }
}
