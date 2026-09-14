package com.denggl2.mason.sync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.denggl2.mason.sync.data.entity.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC, id ASC")
    fun getByConversation(conversationId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC, id ASC")
    suspend fun getByConversationList(conversationId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: Long): Message?

    @Query("SELECT * FROM messages WHERE event_id = :eventId")
    suspend fun getByEventId(eventId: String): Message?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<Message>)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<Message>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("UPDATE messages SET sync_state = :syncState WHERE event_id = :eventId")
    suspend fun updateSyncState(eventId: String, syncState: String): Int
}
