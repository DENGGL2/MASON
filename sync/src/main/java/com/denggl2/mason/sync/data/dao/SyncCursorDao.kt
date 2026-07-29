package com.denggl2.mason.sync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.denggl2.mason.sync.data.entity.SyncCursorEntity

@Dao
interface SyncCursorDao {
    @Query(
        "SELECT * FROM sync_cursors " +
            "WHERE conversation_global_id = :conversationGlobalId AND source_device_id = :sourceDeviceId",
    )
    suspend fun get(conversationGlobalId: String, sourceDeviceId: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(cursor: SyncCursorEntity): Long

    @Query(
        "UPDATE sync_cursors SET sequence = :sequence, updated_at = :updatedAt " +
            "WHERE conversation_global_id = :conversationGlobalId " +
            "AND source_device_id = :sourceDeviceId AND sequence < :sequence",
    )
    suspend fun advance(
        conversationGlobalId: String,
        sourceDeviceId: String,
        sequence: Long,
        updatedAt: Long,
    ): Int
}
