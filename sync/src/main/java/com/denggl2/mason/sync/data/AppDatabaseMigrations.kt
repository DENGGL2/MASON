package com.denggl2.mason.sync.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val database = db
        val migratedAt = System.currentTimeMillis()
        val deviceId = GlobalIdGenerator.newId(migratedAt)
        val conversations = database.readLegacyConversations()
        val messages = database.readLegacyMessages()

        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `local_device` (" +
                "`singleton_id` INTEGER NOT NULL, " +
                "`device_id` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`singleton_id`))",
        )
        database.execSQL(
            "INSERT INTO `local_device` (`singleton_id`, `device_id`, `created_at`) VALUES (1, ?, ?)",
            arrayOf<Any?>(deviceId, migratedAt),
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_local_device_device_id` " +
                "ON `local_device` (`device_id`)",
        )

        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `conversations_v2` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`global_id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`sync_mode` TEXT NOT NULL, " +
                "`authority_device_id` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL)",
        )
        conversations.forEach { conversation ->
            database.execSQL(
                "INSERT INTO `conversations_v2` " +
                    "(`id`, `global_id`, `title`, `sync_mode`, `authority_device_id`, `created_at`, `updated_at`) " +
                    "VALUES (?, ?, ?, 'LOCAL_ONLY', ?, ?, ?)",
                arrayOf<Any?>(
                    conversation.id,
                    GlobalIdGenerator.newId(conversation.createdAt.coerceAtLeast(0)),
                    conversation.title,
                    deviceId,
                    conversation.createdAt,
                    conversation.updatedAt,
                ),
            )
        }

        database.execSQL("DROP TABLE `messages`")
        database.execSQL("DROP TABLE `conversations`")
        database.execSQL("ALTER TABLE `conversations_v2` RENAME TO `conversations`")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `messages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`conversation_id` INTEGER NOT NULL, " +
                "`event_id` TEXT NOT NULL, " +
                "`source_device_id` TEXT NOT NULL, " +
                "`sync_state` TEXT NOT NULL, " +
                "`role` TEXT NOT NULL, " +
                "`content` TEXT, " +
                "`tool_call_id` TEXT, " +
                "`tool_call_name` TEXT, " +
                "`timestamp` INTEGER NOT NULL, " +
                "FOREIGN KEY(`conversation_id`) REFERENCES `conversations`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        messages.forEach { message ->
            database.execSQL(
                "INSERT INTO `messages` " +
                    "(`id`, `conversation_id`, `event_id`, `source_device_id`, `sync_state`, `role`, " +
                    "`content`, `tool_call_id`, `tool_call_name`, `timestamp`) " +
                    "VALUES (?, ?, ?, ?, 'LOCAL_ONLY', ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    message.id,
                    message.conversationId,
                    GlobalIdGenerator.newId(message.timestamp.coerceAtLeast(0)),
                    deviceId,
                    message.role,
                    message.content,
                    message.toolCallId,
                    message.toolCallName,
                    message.timestamp,
                ),
            )
        }
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_global_id` " +
                "ON `conversations` (`global_id`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_messages_conversation_id` " +
                "ON `messages` (`conversation_id`)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_event_id` " +
                "ON `messages` (`event_id`)",
        )

        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_outbox` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`command_id` TEXT NOT NULL, " +
                "`envelope_json` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`attempt_count` INTEGER NOT NULL, " +
                "`next_attempt_at` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`completed_at` INTEGER, " +
                "`last_error` TEXT)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_command_id` " +
                "ON `sync_outbox` (`command_id`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_outbox_status_next_attempt_at` " +
                "ON `sync_outbox` (`status`, `next_attempt_at`)",
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_cursors` (" +
                "`conversation_global_id` TEXT NOT NULL, " +
                "`source_device_id` TEXT NOT NULL, " +
                "`sequence` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`conversation_global_id`, `source_device_id`))",
        )
    }
}

private data class LegacyConversation(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

private data class LegacyMessage(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String?,
    val toolCallId: String?,
    val toolCallName: String?,
    val timestamp: Long,
)

private fun SupportSQLiteDatabase.readLegacyConversations(): List<LegacyConversation> =
    query("SELECT id, title, created_at, updated_at FROM conversations ORDER BY id").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    LegacyConversation(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        createdAt = cursor.getLong(2),
                        updatedAt = cursor.getLong(3),
                    ),
                )
            }
        }
    }

private fun SupportSQLiteDatabase.readLegacyMessages(): List<LegacyMessage> =
    query(
        "SELECT id, conversation_id, role, content, tool_call_id, tool_call_name, timestamp " +
            "FROM messages ORDER BY id",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    LegacyMessage(
                        id = cursor.getLong(0),
                        conversationId = cursor.getLong(1),
                        role = cursor.getString(2),
                        content = cursor.getStringOrNull(3),
                        toolCallId = cursor.getStringOrNull(4),
                        toolCallName = cursor.getStringOrNull(5),
                        timestamp = cursor.getLong(6),
                    ),
                )
            }
        }
    }

private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)
