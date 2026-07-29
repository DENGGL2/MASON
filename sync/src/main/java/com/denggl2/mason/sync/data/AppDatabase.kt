package com.denggl2.mason.sync.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.denggl2.mason.sync.data.dao.ConversationDao
import com.denggl2.mason.sync.data.dao.LocalDeviceDao
import com.denggl2.mason.sync.data.dao.MessageDao
import com.denggl2.mason.sync.data.dao.SyncCursorDao
import com.denggl2.mason.sync.data.dao.SyncOutboxDao
import com.denggl2.mason.sync.data.entity.Conversation
import com.denggl2.mason.sync.data.entity.LocalDevice
import com.denggl2.mason.sync.data.entity.Message
import com.denggl2.mason.sync.data.entity.SyncCursorEntity
import com.denggl2.mason.sync.data.entity.SyncOutboxEntry

@Database(
    entities = [
        Conversation::class,
        Message::class,
        LocalDevice::class,
        SyncOutboxEntry::class,
        SyncCursorEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun localDeviceDao(): LocalDeviceDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncCursorDao(): SyncCursorDao
}
