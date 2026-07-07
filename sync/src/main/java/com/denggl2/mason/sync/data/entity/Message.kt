package com.denggl2.mason.sync.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversation_id")],
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "content")
    val content: String?,

    @ColumnInfo(name = "tool_call_id")
    val toolCallId: String? = null,

    @ColumnInfo(name = "tool_call_name")
    val toolCallName: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
)
