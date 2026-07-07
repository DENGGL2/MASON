package com.denggl2.mason.tool

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "sms"
    override val description = "短信管理：发送短信、列出最近短信、按关键词搜索短信"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：send（发送短信）、list（列出最近短信）、search（按关键词搜索）",
            required = true,
            enum = listOf("send", "list", "search"),
        ),
        "phone_number" to ParameterDef(
            type = "string",
            description = "目标电话号码，send 时必填",
            required = false,
        ),
        "message" to ParameterDef(
            type = "string",
            description = "短信内容，send 时必填",
            required = false,
        ),
        "query" to ParameterDef(
            type = "string",
            description = "搜索关键词，按短信内容搜索，search 时必填",
            required = false,
        ),
        "limit" to ParameterDef(
            type = "integer",
            description = "最大返回数量，list/search 时生效，默认 20",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: "list"
        val limit = args["limit"]?.toIntOrNull() ?: 20

        return try {
            when (action) {
                "send" -> sendSms(args)
                "list" -> listSms(limit)
                "search" -> {
                    val query = args["query"]
                    if (query.isNullOrBlank()) {
                        return ToolResult(
                            success = false,
                            error = "search 操作需要提供 query 参数（短信内容关键词）"
                        )
                    }
                    searchSms(query, limit)
                }
                else -> ToolResult(
                    success = false,
                    error = "未知的 action: $action，支持 send、list 和 search"
                )
            }
        } catch (e: Exception) {
            ToolResult(success = false, error = "短信操作失败: ${e.message}")
        }
    }

    private fun sendSms(args: Map<String, String>): ToolResult {
        // Check SEND_SMS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(
                success = false,
                error = "需要 SEND_SMS 权限才能发送短信。请在系统设置中授予该权限。"
            )
        }

        // Android 4.4+ check default SMS app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            if (defaultSmsPackage == null || defaultSmsPackage != context.packageName) {
                return ToolResult(
                    success = false,
                    error = "Mason 未被设置为默认短信应用。请在系统设置中将 Mason 设为默认短信应用后才能发送短信。"
                )
            }
        }

        val phoneNumber = args["phone_number"]
        if (phoneNumber.isNullOrBlank()) {
            return ToolResult(
                success = false,
                error = "send 操作需要提供 phone_number 参数"
            )
        }

        val message = args["message"]
        if (message.isNullOrBlank()) {
            return ToolResult(
                success = false,
                error = "send 操作需要提供 message 参数"
            )
        }

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(phoneNumber, null, message, null, null)

            ToolResult(
                success = true,
                data = mapOf(
                    "status" to "sent",
                    "phone_number" to phoneNumber,
                    "message_preview" to if (message.length > 50) message.take(50) + "..." else message,
                )
            )
        } catch (e: SecurityException) {
            ToolResult(
                success = false,
                error = "发送短信权限不足: ${e.message}"
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "发送短信失败: ${e.message}"
            )
        }
    }

    private fun listSms(limit: Int): ToolResult {
        // Check READ_SMS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(
                success = false,
                error = "需要 READ_SMS 权限才能读取短信。请在系统设置中授予该权限。"
            )
        }

        return querySms(null, null, limit)
    }

    private fun searchSms(query: String, limit: Int): ToolResult {
        // Check READ_SMS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(
                success = false,
                error = "需要 READ_SMS 权限才能读取短信。请在系统设置中授予该权限。"
            )
        }

        val selection = "${Telephony.Sms.BODY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        return querySms(selection, selectionArgs, limit, query)
    }

    private fun querySms(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int,
        searchQuery: String? = null,
    ): ToolResult {
        val messages = mutableListOf<Map<String, String>>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            "date DESC LIMIT $limit"
        )

        cursor?.use {
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext() && messages.size < limit) {
                val address = it.getString(addressIndex) ?: "未知"
                val body = it.getString(bodyIndex) ?: ""
                val date = it.getLong(dateIndex)
                val type = when (it.getInt(typeIndex)) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "收"
                    Telephony.Sms.MESSAGE_TYPE_SENT -> "发"
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> "草稿"
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "待发"
                    else -> "其他"
                }

                val time = dateFormat.format(Date(date))

                messages.add(
                    mapOf(
                        "type" to type,
                        "address" to address,
                        "time" to time,
                        "body" to body,
                    )
                )
            }
        }

        if (messages.isEmpty()) {
            val prefix = if (searchQuery != null) "搜索 \"$searchQuery\" 未找到" else "未找到"
            return ToolResult(
                success = true,
                data = mapOf("count" to "0", "messages" to "${prefix}短信记录")
            )
        }

        val result = buildString {
            if (searchQuery != null) {
                appendLine("搜索 \"$searchQuery\" 找到 ${messages.size} 条短信：")
            } else {
                appendLine("最近 ${messages.size} 条短信：")
            }
            appendLine()

            messages.forEachIndexed { index, msg ->
                val typeLabel = msg["type"]
                val address = msg["address"]
                val time = msg["time"]
                val body = msg["body"] ?: ""
                val bodyPreview = if (body.length > 80) body.take(80) + "..." else body

                appendLine("${index + 1}. [$typeLabel] $address ($time)")
                appendLine("   $bodyPreview")
                appendLine()
            }
        }

        val label = searchQuery ?: "list"
        return ToolResult(
            success = true,
            data = mapOf(
                "count" to messages.size.toString(),
                "query_type" to label,
                "messages" to result,
            )
        )
    }
}
