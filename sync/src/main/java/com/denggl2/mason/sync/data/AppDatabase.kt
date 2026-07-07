package com.denggl2.mason.sync.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.denggl2.mason.sync.data.dao.ConversationDao
import com.denggl2.mason.sync.data.dao.MessageDao
import com.denggl2.mason.sync.data.entity.Conversation
import com.denggl2.mason.sync.data.entity.Message

@Database(
    entities = [Conversation::class, Message::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
