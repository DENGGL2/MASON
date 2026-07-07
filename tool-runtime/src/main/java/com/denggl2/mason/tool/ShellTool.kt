package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class ShellTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "shell"
    override val description = "在设备上执行 shell 命令（白名单限制）"
    override val parameters: Map<String, ParameterDef> = mapOf(        "command" to ParameterDef("string", "要执行的命令", required = true))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val cmd = args["command"] ?: return ToolResult.error("Missing command")
        val whitelist = listOf("ls", "cat", "echo", "pwd", "df", "du", "ps", "top", "uname", "whoami", "date", "uptime", "id", "printenv", "logcat", "dumpsys", "pm list", "getprop", "setprop", "input", "am start", "am broadcast", "am force-stop", "service", "wm", "settings", "cmd", "bugreportz", "ping", "ip")
        if (whitelist.none { cmd.startsWith(it) }) return ToolResult.error("Command not in whitelist")
        if (cmd.contains("|") || cmd.contains(">") || cmd.contains("<")) return ToolResult.error("Pipes and redirects disabled")
        val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        val err = proc.errorStream.bufferedReader().use { it.readText() }
        proc.waitFor()
        return ToolResult.success(mapOf("stdout" to out, "stderr" to err, "exit" to proc.exitValue().toString()))
    }
}
