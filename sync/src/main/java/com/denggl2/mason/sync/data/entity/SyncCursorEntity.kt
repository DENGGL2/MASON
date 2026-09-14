package com.denggl2.mason.sync.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "sync_cursors",
    primaryKeys = ["conversation_global_id", "source_device_id"],
)
data class SyncCursorEntity(
    @ColumnInfo(name = "conversation_global_id")
    val conversationGlobalId: String,

    @ColumnInfo(name = "source_device_id")
    val sourceDeviceId: String,

    @ColumnInfo(name = "sequence")
    val sequence: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
