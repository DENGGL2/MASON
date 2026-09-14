package com.denggl2.mason.sync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.denggl2.mason.sync.data.entity.OutboxStatuses
import com.denggl2.mason.sync.data.entity.SyncOutboxEntry

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: SyncOutboxEntry): Long

    @Query("SELECT * FROM sync_outbox WHERE command_id = :commandId")
    suspend fun getByCommandId(commandId: String): SyncOutboxEntry?

    @Query(
        "SELECT * FROM sync_outbox " +
            "WHERE status IN ('PENDING', 'FAILED') AND next_attempt_at <= :now " +
            "ORDER BY created_at ASC, id ASC LIMIT :limit",
    )
    suspend fun getReady(now: Long, limit: Int): List<SyncOutboxEntry>

    @Query(
        "UPDATE sync_outbox SET status = 'IN_FLIGHT', attempt_count = attempt_count + 1, last_error = NULL " +
            "WHERE id = :id AND status IN ('PENDING', 'FAILED')",
    )
    suspend fun markInFlight(id: Long): Int

    @Query(
        "UPDATE sync_outbox SET status = 'COMPLETED', completed_at = :completedAt, last_error = NULL " +
            "WHERE id = :id AND status = 'IN_FLIGHT'",
    )
    suspend fun markCompleted(id: Long, completedAt: Long): Int

    @Query(
        "UPDATE sync_outbox SET status = 'FAILED', next_attempt_at = :nextAttemptAt, last_error = :error " +
            "WHERE id = :id AND status = 'IN_FLIGHT'",
    )
    suspend fun markFailed(id: Long, nextAttemptAt: Long, error: String): Int

    @Query(
        "UPDATE sync_outbox SET status = 'PENDING', next_attempt_at = :now " +
            "WHERE status = 'IN_FLIGHT'",
    )
    suspend fun requeueInterrupted(now: Long): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status != '${OutboxStatuses.COMPLETED}'")
    suspend fun pendingCount(): Int
}
