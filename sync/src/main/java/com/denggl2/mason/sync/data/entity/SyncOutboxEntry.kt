package com.denggl2.mason.sync.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["command_id"], unique = true),
        Index(value = ["status", "next_attempt_at"]),
    ],
)
data class SyncOutboxEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "command_id")
    val commandId: String,

    @ColumnInfo(name = "envelope_json")
    val envelopeJson: String,

    @ColumnInfo(name = "status")
    val status: String = OutboxStatuses.PENDING,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
)

object OutboxStatuses {
    const val PENDING = "PENDING"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}
