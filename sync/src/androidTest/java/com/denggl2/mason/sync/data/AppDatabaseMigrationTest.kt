package com.denggl2.mason.sync.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.denggl2.mason.protocol.CommandResult
import com.denggl2.mason.sync.OutboxFlushSummary
import com.denggl2.mason.sync.SyncCommandSender
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.data.entity.ConversationSyncModes
import com.denggl2.mason.sync.data.entity.MessageSyncStates
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var databaseName: String
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        databaseName = "mason-migration-${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesDataAndOutboxReconnectIsIdempotent() = runBlocking {
        createVersionOneDatabase()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        database = migrated

        val conversations = migrated.conversationDao().getAllList()
        val messages = migrated.messageDao().getByConversationList(7)
        val localDevice = migrated.localDeviceDao().get()

        assertEquals(1, conversations.size)
        assertEquals(7, conversations.single().id)
        assertEquals("旧对话", conversations.single().title)
        assertEquals(ConversationSyncModes.LOCAL_ONLY, conversations.single().syncMode)
        assertEquals(7, UUID.fromString(conversations.single().globalId).version())
        assertEquals(localDevice?.deviceId, conversations.single().authorityDeviceId)
        assertEquals(listOf(11L, 12L), messages.map { it.id })
        assertEquals(listOf("旧问题", "旧回答"), messages.map { it.content })
        assertEquals(2, messages.map { it.eventId }.distinct().size)
        assertTrue(messages.all { it.sourceDeviceId == localDevice?.deviceId })
        assertTrue(messages.all { it.syncState == MessageSyncStates.LOCAL_ONLY })

        val manager = SyncManager(context, migrated)
        manager.saveMessage(7, role = "user", content = "仅本地")
        assertEquals(0, manager.getPendingOutboxCount())

        manager.updateConversationSyncMode(7, ConversationSyncModes.SHARED)
        manager.saveMessage(7, role = "user", content = "等待同步")
        assertEquals(1, manager.getPendingOutboxCount())
        val flushAt = System.currentTimeMillis() + 1_000

        var sendCount = 0
        val sender = SyncCommandSender { command ->
            sendCount++
            CommandResult(
                commandId = command.commandId,
                accepted = true,
                completedAt = 10_000,
            )
        }
        val firstFlush = manager.flushOutbox(sender, now = flushAt)
        val secondFlush = manager.flushOutbox(sender, now = flushAt)

        assertEquals(OutboxFlushSummary(attempted = 1, completed = 1, failed = 0), firstFlush)
        assertEquals(OutboxFlushSummary(attempted = 0, completed = 0, failed = 0), secondFlush)
        assertEquals(1, sendCount)
        assertEquals(0, manager.getPendingOutboxCount())
        assertEquals(
            MessageSyncStates.SYNCED,
            migrated.messageDao().getLastMessage(7)?.syncState,
        )

        val conversationGlobalId = conversations.single().globalId
        assertEquals(5L, manager.advanceSyncCursor(conversationGlobalId, "windows-1", 5, 20_000))
        assertEquals(5L, manager.advanceSyncCursor(conversationGlobalId, "windows-1", 3, 21_000))
        assertEquals(5L, manager.getSyncCursor(conversationGlobalId, "windows-1"))
    }

    private fun createVersionOneDatabase() {
        val path = context.getDatabasePath(databaseName)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.execSQL("PRAGMA foreign_keys = ON")
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `conversations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL)",
            )
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `messages` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`conversation_id` INTEGER NOT NULL, " +
                    "`role` TEXT NOT NULL, " +
                    "`content` TEXT, " +
                    "`tool_call_id` TEXT, " +
                    "`tool_call_name` TEXT, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`conversation_id`) REFERENCES `conversations`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_messages_conversation_id` " +
                    "ON `messages` (`conversation_id`)",
            )
            legacy.execSQL(
                "INSERT INTO conversations (id, title, created_at, updated_at) VALUES (7, '旧对话', 1000, 2000)",
            )
            legacy.execSQL(
                "INSERT INTO messages " +
                    "(id, conversation_id, role, content, tool_call_id, tool_call_name, timestamp) " +
                    "VALUES (11, 7, 'user', '旧问题', NULL, NULL, 1100)",
            )
            legacy.execSQL(
                "INSERT INTO messages " +
                    "(id, conversation_id, role, content, tool_call_id, tool_call_name, timestamp) " +
                    "VALUES (12, 7, 'assistant', '旧回答', NULL, NULL, 1200)",
            )
            legacy.version = 1
        }
    }
}
