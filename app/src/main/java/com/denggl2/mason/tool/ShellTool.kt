package com.denggl2.mason.tool

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellTool @Inject constructor() : Tool {
    override val name = "run_shell"
    override val description = "执行只读 shell 命令（白名单限制，超时 10 秒）"
    override val parameters = mapOf(
        "command" to ParameterDef(
            type = "string",
            description = "要执行的 shell 命令",
            required = true,
        ),
    )

    companion object {
        private const val TIMEOUT_SECONDS = 10L

        private val ALLOWED_COMMANDS = listOf(
            "pm list",
            "pm path",
            "dumpsys package",
            "dumpsys battery",
            "dumpsys meminfo",
            "dumpsys cpuinfo",
            "dumpsys diskstats",
            "dumpsys activity",
            "dumpsys window",
            "dumpsys usagestats",
            "dumpsys netstats",
            "am start",
            "am broadcast",
            "am force-stop",
            "logcat -d",
            "logcat -t",
            "getprop",
            "settings list",
            "settings get",
            "cmd package list",
            "cmd package path",
            "df",
            "ls",
            "cat",
            "ps",
            "top -n",
            "uptime",
            "uname -a",
            "wm size",
            "wm density",
            "input keyevent",
        )
    }

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val command = args["command"] ?: return ToolResult(
            success = false,
            error = "缺少 command 参数",
        )

        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) {
            return ToolResult(success = false, error = "命令不能为空")
        }

        // 白名单校验
        val allowed = ALLOWED_COMMANDS.any { prefix ->
            trimmedCommand.startsWith(prefix, ignoreCase = true)
        }

        if (!allowed) {
            return ToolResult(
                success = false,
                error = "不允许的命令。允许的命令前缀: ${ALLOWED_COMMANDS.joinToString(", ")}",
            )
        }

        // 禁止管道、重定向等危险操作
        val dangerousChars = setOf('|', ';', '&', '`', '$', '>', '<')
        if (dangerousChars.any { it in trimmedCommand }) {
            return ToolResult(
                success = false,
                error = "命令包含不允许的字符: | ; & ` $ > <",
            )
        }

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", trimmedCommand))
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val stdoutThread = Thread {
                stdoutReader.use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stdout.appendLine(line)
                        line = reader.readLine()
                    }
                }
            }

            val stderrThread = Thread {
                stderrReader.use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stderr.appendLine(line)
                        line = reader.readLine()
                    }
                }
            }

            stdoutThread.start()
            stderrThread.start()

            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            stdoutThread.join(2000)
            stderrThread.join(2000)

            if (!finished) {
                process.destroyForcibly()
                return ToolResult(
                    success = false,
                    error = "命令执行超时（${TIMEOUT_SECONDS}秒）",
                )
            }

            val exitCode = process.exitValue()
            ToolResult(
                success = exitCode == 0,
                data = mapOf(
                    "exit_code" to exitCode.toString(),
                    "stdout" to stdout.toString().trimEnd(),
                    "stderr" to stderr.toString().trimEnd(),
                ),
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "命令执行失败: ${e.message}",
            )
        }
    }
}
