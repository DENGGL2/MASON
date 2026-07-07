package com.denggl2.mason.tool

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "contacts"
    override val description = "读取联系人信息：列出最近联系人、按姓名或号码搜索联系人"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：list（列出最近联系人）、search（按姓名或号码搜索）",
            required = true,
            enum = listOf("list", "search"),
        ),
        "query" to ParameterDef(
            type = "string",
            description = "搜索关键词，按姓名或电话号码模糊搜索，search 时必填",
            required = false,
        ),
        "limit" to ParameterDef(
            type = "integer",
            description = "最大返回数量，默认 20",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        // Check READ_CONTACTS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(
                success = false,
                error = "需要 READ_CONTACTS 权限才能读取联系人。请在系统设置中授予该权限。"
            )
        }

        val action = args["action"] ?: "list"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val query = args["query"]

        return try {
            when (action) {
                "list" -> listContacts(limit)
                "search" -> {
                    if (query.isNullOrBlank()) {
                        return ToolResult(
                            success = false,
                            error = "search 操作需要提供 query 参数（姓名或号码关键词）"
                        )
                    }
                    searchContacts(query, limit)
                }
                else -> ToolResult(
                    success = false,
                    error = "未知的 action: $action，支持 list 和 search"
                )
            }
        } catch (e: Exception) {
            ToolResult(success = false, error = "读取联系人失败: ${e.message}")
        }
    }

    private fun listContacts(limit: Int): ToolResult {
        val contacts = mutableListOf<Map<String, String>>()

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )

        val cursor: android.database.Cursor? = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC LIMIT $limit"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (it.moveToNext() && contacts.size < limit) {
                val contactId = it.getString(idIndex)
                val displayName = it.getString(nameIndex) ?: "未知"
                val hasPhone = it.getInt(hasPhoneIndex) > 0

                val phones = if (hasPhone) getContactPhones(contactId) else emptyList()
                val emails = getContactEmails(contactId)

                contacts.add(
                    mapOf(
                        "id" to contactId,
                        "name" to displayName,
                        "phones" to phones.joinToString("; "),
                        "emails" to emails.joinToString("; "),
                    )
                )
            }
        }

        if (contacts.isEmpty()) {
            return ToolResult(
                success = true,
                data = mapOf("count" to "0", "contacts" to "未找到联系人")
            )
        }

        val result = buildString {
            appendLine("找到 ${contacts.size} 个联系人：")
            appendLine()
            contacts.forEachIndexed { index, contact ->
                appendLine("${index + 1}. ${contact["name"]}")
                val phones = contact["phones"]
                if (!phones.isNullOrBlank()) {
                    appendLine("   电话: $phones")
                }
                val emails = contact["emails"]
                if (!emails.isNullOrBlank()) {
                    appendLine("   邮箱: $emails")
                }
                appendLine()
            }
        }

        return ToolResult(
            success = true,
            data = mapOf("count" to contacts.size.toString(), "contacts" to result)
        )
    }

    private fun searchContacts(query: String, limit: Int): ToolResult {
        val contacts = mutableListOf<Map<String, String>>()

        // Search by display name
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )

        val selection =
            "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val cursor: android.database.Cursor? = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC LIMIT $limit"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (it.moveToNext() && contacts.size < limit) {
                val contactId = it.getString(idIndex)
                val displayName = it.getString(nameIndex) ?: "未知"
                val hasPhone = it.getInt(hasPhoneIndex) > 0

                val phones = if (hasPhone) getContactPhones(contactId) else emptyList()
                val emails = getContactEmails(contactId)

                contacts.add(
                    mapOf(
                        "id" to contactId,
                        "name" to displayName,
                        "phones" to phones.joinToString("; "),
                        "emails" to emails.joinToString("; "),
                    )
                )
            }
        }

        // Also search by phone number if name search didn't fill limit
        if (contacts.size < limit) {
            searchByPhoneNumber(query, limit - contacts.size, contacts)
        }

        if (contacts.isEmpty()) {
            return ToolResult(
                success = true,
                data = mapOf("count" to "0", "contacts" to "未找到匹配 \"$query\" 的联系人")
            )
        }

        val result = buildString {
            appendLine("搜索 \"$query\" 找到 ${contacts.size} 个联系人：")
            appendLine()
            contacts.forEachIndexed { index, contact ->
                appendLine("${index + 1}. ${contact["name"]}")
                val phones = contact["phones"]
                if (!phones.isNullOrBlank()) {
                    appendLine("   电话: $phones")
                }
                val emails = contact["emails"]
                if (!emails.isNullOrBlank()) {
                    appendLine("   邮箱: $emails")
                }
                appendLine()
            }
        }

        return ToolResult(
            success = true,
            data = mapOf("count" to contacts.size.toString(), "contacts" to result)
        )
    }

    private fun searchByPhoneNumber(
        query: String,
        limit: Int,
        contacts: MutableList<Map<String, String>>,
    ) {
        val existingIds = contacts.map { it["id"] }.toSet()

        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val phoneProjection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )

        val phoneSelection =
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val phoneArgs = arrayOf("%$query%")

        val phoneCursor = context.contentResolver.query(
            phoneUri, phoneProjection, phoneSelection, phoneArgs, null
        )

        phoneCursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext() && contacts.size < limit) {
                val contactId = it.getString(idIndex)
                if (contactId in existingIds) continue

                val displayName = it.getString(nameIndex) ?: "未知"
                val number = it.getString(numberIndex) ?: ""
                val emails = getContactEmails(contactId)

                contacts.add(
                    mapOf(
                        "id" to contactId,
                        "name" to displayName,
                        "phones" to number,
                        "emails" to emails.joinToString("; "),
                    )
                )
            }
        }
    }

    private fun getContactPhones(contactId: String): List<String> {
        val phones = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )
        cursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                phones.add(it.getString(numberIndex) ?: "")
            }
        }
        return phones
    }

    private fun getContactEmails(contactId: String): List<String> {
        val emails = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )
        cursor?.use {
            val emailIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (it.moveToNext()) {
                emails.add(it.getString(emailIndex) ?: "")
            }
        }
        return emails
    }
}
