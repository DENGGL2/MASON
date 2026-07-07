package com.denggl2.mason.sync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.denggl2.mason.sync.data.entity.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun getAll(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation): Long

    @Update
    suspend fun update(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    suspend fun getAllList(): List<Conversation>
}
