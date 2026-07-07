package com.denggl2.mason.tool

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "calendar"
    override val description = "日历管理：列出指定日期范围的事件、查看未来7天事件、创建新事件"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：list（列出指定日期事件）、upcoming（未来7天事件）、create（创建事件）",
            required = true,
            enum = listOf("list", "upcoming", "create"),
        ),
        "start_date" to ParameterDef(
            type = "string",
            description = "起始日期 (yyyy-MM-dd)，list 时生效，默认今天",
            required = false,
        ),
        "end_date" to ParameterDef(
            type = "string",
            description = "结束日期 (yyyy-MM-dd)，list 时生效，默认今天",
            required = false,
        ),
        "title" to ParameterDef(
            type = "string",
            description = "事件标题，create 时必填",
            required = false,
        ),
        "description" to ParameterDef(
            type = "string",
            description = "事件描述，create 时可选",
            required = false,
        ),
        "start_time" to ParameterDef(
            type = "string",
            description = "事件开始时间 (ISO 8601)，create 时必填",
            required = false,
        ),
        "end_time" to ParameterDef(
            type = "string",
            description = "事件结束时间 (ISO 8601)，create 时必填",
            required = false,
        ),
        "location" to ParameterDef(
            type = "string",
            description = "事件地点，create 时可选",
            required = false,
        ),
        "calendar_id" to ParameterDef(
            type = "integer",
            description = "日历 ID，可选，默认使用第一个可见日历",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        // Check permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(
                success = false,
                error = "缺少读取日历权限 (READ_CALENDAR)，请在系统设置中授予权限",
            )
        }

        val action = args["action"] ?: return ToolResult(
            success = false, error = "缺少 action 参数"
        )

        return when (action) {
            "list" -> listEvents(args["start_date"], args["end_date"])
            "upcoming" -> listUpcoming()
            "create" -> createEvent(args)
            else -> ToolResult(success = false, error = "未知 action: $action")
        }
    }

    private fun listEvents(startDateStr: String?, endDateStr: String?): ToolResult {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dateFormat.timeZone = TimeZone.getDefault()
        val now = Date()

        val startDate = if (!startDateStr.isNullOrBlank()) {
            try { dateFormat.parse(startDateStr) } catch (e: Exception) { null }
        } else now
        val endDate = if (!endDateStr.isNullOrBlank()) {
            try { dateFormat.parse(endDateStr) } catch (e: Exception) { null }
        } else now

        if (startDate == null || endDate == null) {
            return ToolResult(success = false, error = "日期格式无效，请使用 yyyy-MM-dd 格式")
        }

        // Set end date to end of day
        endDate.time = endDate.time + 24 * 60 * 60 * 1000 - 1

        return try {
            val events = queryEvents(startDate.time, endDate.time)
            if (events.isEmpty()) {
                ToolResult(
                    success = true,
                    data = mapOf("message" to "该日期范围内没有事件"),
                )
            } else {
                ToolResult(
                    success = true,
                    data = mapOf(
                        "count" to events.size.toString(),
                        "start_date" to dateFormat.format(startDate),
                        "end_date" to dateFormat.format(endDate),
                        "events" to events.joinToString("\n---\n"),
                    ),
                )
            }
        } catch (e: SecurityException) {
            ToolResult(success = false, error = "权限不足，无法读取日历: ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, error = "查询日历事件失败: ${e.message}")
        }
    }

    private fun listUpcoming(): ToolResult {
        val now = System.currentTimeMillis()
        val sevenDaysLater = now + 7 * 24 * 60 * 60 * 1000

        return try {
            val events = queryEvents(now, sevenDaysLater)
            if (events.isEmpty()) {
                ToolResult(
                    success = true,
                    data = mapOf("message" to "未来 7 天没有事件"),
                )
            } else {
                ToolResult(
                    success = true,
                    data = mapOf(
                        "count" to events.size.toString(),
                        "range" to "未来 7 天",
                        "events" to events.joinToString("\n---\n"),
                    ),
                )
            }
        } catch (e: Exception) {
            ToolResult(success = false, error = "查询未来事件失败: ${e.message}")
        }
    }

    private fun createEvent(args: Map<String, String>): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(
                success = false,
                error = "缺少写入日历权限 (WRITE_CALENDAR)，请在系统设置中授予权限",
            )
        }

        val title = args["title"]
        if (title.isNullOrBlank()) {
            return ToolResult(success = false, error = "create 操作需要 title 参数")
        }

        val startTimeStr = args["start_time"]
        val endTimeStr = args["end_time"]
        if (startTimeStr.isNullOrBlank() || endTimeStr.isNullOrBlank()) {
            return ToolResult(success = false, error = "create 操作需要 start_time 和 end_time 参数 (ISO 8601)")
        }

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
        val startTime = try { isoFormat.parse(startTimeStr)?.time } catch (e: Exception) { null }
        val endTime = try { isoFormat.parse(endTimeStr)?.time } catch (e: Exception) { null }

        if (startTime == null || endTime == null) {
            return ToolResult(success = false, error = "时间格式错误，请使用 ISO 8601 格式 (如 2026-07-07T14:00:00)")
        }

        return try {
            val calendarId = args["calendar_id"]?.toLongOrNull() ?: getDefaultCalendarId()

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                args["description"]?.let { put(CalendarContract.Events.DESCRIPTION, it) }
                args["location"]?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            } else {
                @Suppress("DEPRECATION")
                context.contentResolver.insert(Uri.parse("content://com.android.calendar/events"), values)
            }

            if (uri != null) {
                val eventId = ContentUris.parseId(uri)
                ToolResult(
                    success = true,
                    data = mapOf(
                        "status" to "created",
                        "event_id" to eventId.toString(),
                        "title" to title,
                        "calendar_id" to calendarId.toString(),
                    ),
                )
            } else {
                ToolResult(success = false, error = "创建事件失败：无法插入日历")
            }
        } catch (e: SecurityException) {
            ToolResult(success = false, error = "权限不足，无法创建日历事件: ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, error = "创建日历事件失败: ${e.message}")
        }
    }

    private fun queryEvents(startMillis: Long, endMillis: Long): List<String> {
        val events = mutableListOf<String>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.CALENDAR_ID,
        )

        val selection = "${CalendarContract.Events.DTSTART} < ? AND ${CalendarContract.Events.DTEND} > ?"
        val selectionArgs = arrayOf(endMillis.toString(), startMillis.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
                val locIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val title = it.getString(titleIdx) ?: "无标题"
                    val start = it.getLong(startIdx)
                    val end = it.getLong(endIdx)
                    val loc = it.getString(locIdx) ?: ""
                    val desc = it.getString(descIdx) ?: ""

                    val sb = StringBuilder()
                    sb.appendLine("ID: $id")
                    sb.appendLine("标题: $title")
                    sb.appendLine("开始: ${dateFormat.format(Date(start))}")
                    sb.appendLine("结束: ${dateFormat.format(Date(end))}")
                    if (loc.isNotBlank()) sb.appendLine("地点: $loc")
                    if (desc.isNotBlank()) sb.appendLine("描述: $desc")
                    events.add(sb.toString().trimEnd())
                }
            }
        } catch (e: Exception) {
            throw e
        }

        return events
    }

    private fun getDefaultCalendarId(): Long {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                null,
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getLong(0)
                }
            }
        } catch (_: Exception) {
        }
        return 1L // Default calendar ID fallback
    }
}
