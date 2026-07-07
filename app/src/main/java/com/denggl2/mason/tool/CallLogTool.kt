package com.denggl2.mason.tool

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "call_log"
    override val description = "读取通话记录：最近通话记录、未接来电"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：recent（最近通话记录）、missed（未接来电）",
            required = true,
            enum = listOf("recent", "missed"),
        ),
        "limit" to ParameterDef(
            type = "integer",
            description = "最大返回数量，默认 20",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        // Check READ_CALL_LOG permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(
                success = false,
                error = "需要 READ_CALL_LOG 权限才能读取通话记录。请在系统设置中授予该权限。"
            )
        }

        val action = args["action"] ?: "recent"
        val limit = args["limit"]?.toIntOrNull() ?: 20

        return try {
            when (action) {
                "recent" -> queryCallLog(null, null, limit, "最近通话记录")
                "missed" -> {
                    val selection = "${CallLog.Calls.TYPE} = ?"
                    val selectionArgs = arrayOf(CallLog.Calls.MISSED_TYPE.toString())
                    queryCallLog(selection, selectionArgs, limit, "未接来电")
                }
                else -> ToolResult(
                    success = false,
                    error = "未知的 action: $action，支持 recent 和 missed"
                )
            }
        } catch (e: Exception) {
            ToolResult(success = false, error = "读取通话记录失败: ${e.message}")
        }
    }

    private fun queryCallLog(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int,
        label: String,
    ): ToolResult {
        val calls = mutableListOf<Map<String, String>>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        )

        cursor?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext() && calls.size < limit) {
                val number = it.getString(numberIndex) ?: "未知号码"
                val cachedName = it.getString(nameIndex)
                val type = when (it.getInt(typeIndex)) {
                    CallLog.Calls.INCOMING_TYPE -> "呼入"
                    CallLog.Calls.OUTGOING_TYPE -> "呼出"
                    CallLog.Calls.MISSED_TYPE -> "未接"
                    CallLog.Calls.VOICEMAIL_TYPE -> "语音信箱"
                    CallLog.Calls.REJECTED_TYPE -> "拒接"
                    CallLog.Calls.BLOCKED_TYPE -> "拦截"
                    else -> "其他"
                }
                val date = it.getLong(dateIndex)
                val duration = it.getLong(durationIndex) // seconds

                val time = dateFormat.format(Date(date))
                val durationStr = formatDuration(duration)
                val displayName = cachedName ?: number

                calls.add(
                    mapOf(
                        "number" to number,
                        "name" to (cachedName ?: "未知"),
                        "display" to displayName,
                        "type" to type,
                        "time" to time,
                        "duration" to durationStr,
                    )
                )
            }
        }

        if (calls.isEmpty()) {
            return ToolResult(
                success = true,
                data = mapOf("count" to "0", "call_log" to "未找到通话记录")
            )
        }

        val result = buildString {
            appendLine("$label（${calls.size} 条）：")
            appendLine()

            calls.forEachIndexed { index, call ->
                val displayName = call["display"]
                val type = call["type"]
                val time = call["time"]
                val duration = call["duration"]
                val number = call["number"]

                val header = if (displayName != number) {
                    "${index + 1}. $displayName ($number)"
                } else {
                    "${index + 1}. $number"
                }

                appendLine("$header")
                appendLine("   类型: $type  时间: $time  时长: $duration")
                appendLine()
            }
        }

        return ToolResult(
            success = true,
            data = mapOf(
                "count" to calls.size.toString(),
                "call_log" to result,
            )
        )
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0秒"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (hours > 0) append("${hours}时")
            if (minutes > 0) append("${minutes}分")
            append("${secs}秒")
        }
    }
}
