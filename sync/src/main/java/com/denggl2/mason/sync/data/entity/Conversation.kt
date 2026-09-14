package com.denggl2.mason.sync.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.denggl2.mason.sync.data.GlobalIdGenerator

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["global_id"], unique = true)],
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "global_id")
    val globalId: String = GlobalIdGenerator.newId(),

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "sync_mode")
    val syncMode: String = ConversationSyncModes.LOCAL_ONLY,

    @ColumnInfo(name = "authority_device_id")
    val authorityDeviceId: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

object ConversationSyncModes {
    const val LOCAL_ONLY = "LOCAL_ONLY"
    const val SHARED = "SHARED"
    const val REMOTE_MIRROR = "REMOTE_MIRROR"
}
