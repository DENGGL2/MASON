package com.denggl2.mason.tool

import android.os.Build
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.denggl2.mason.tool.Tool
import com.denggl2.mason.tool.ToolResult
import com.denggl2.mason.tool.ParameterDef
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CpuTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "get_cpu_info"
    override val description = "获取CPU详细信息：型号、架构、核心数、最大频率、支持的指令集"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val info = mutableMapOf<String, String>()

        // CPU 架构
        info["arch"] = Build.SUPPORTED_ABIS.joinToString(", ")

        // 核心数
        info["cores"] = Runtime.getRuntime().availableProcessors().toString()

        // 从 /proc/cpuinfo 读取型号
        try {
            val cpuInfo = File("/proc/cpuinfo")
            cpuInfo.bufferedReader().use { reader ->
                reader.lines().forEach { line ->
                    when {
                        line.startsWith("Hardware") -> info["hardware"] = line.substringAfter(":").trim()
                        line.startsWith("Processor") -> info["processor"] = line.substringAfter(":").trim()
                        line.startsWith("CPU part") -> info["cpu_part"] = line.substringAfter(":").trim()
                        line.startsWith("CPU implementer") -> info["implementer"] = line.substringAfter(":").trim()
                        line.startsWith("CPU variant") -> info["variant"] = line.substringAfter(":").trim()
                        line.startsWith("CPU revision") -> info["revision"] = line.substringAfter(":").trim()
                        line.startsWith("Features") -> info["features"] = line.substringAfter(":").trim()
                        line.startsWith("BogoMIPS") -> {
                            val mips = line.substringAfter(":").trim()
                            if (!info.containsKey("bogomips")) info["bogomips"] = mips
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 最大频率 (需要 root，这里读理论值)
        try {
            val freqFiles = File("/sys/devices/system/cpu/cpu0/cpufreq")
            if (freqFiles.exists()) {
                val maxFreq = File(freqFiles, "cpuinfo_max_freq")
                if (maxFreq.exists()) {
                    val khz = maxFreq.readText().trim().toLongOrNull()
                    if (khz != null) info["max_freq_mhz"] = "${khz / 1000} MHz"
                }
            }
        } catch (_: Exception) {}

        if (info.isEmpty()) info["note"] = "部分信息需要root权限，当前仅提供基础数据"

        return ToolResult(success = true, data = info)
    }
}
