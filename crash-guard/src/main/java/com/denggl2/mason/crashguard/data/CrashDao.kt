package com.denggl2.mason.crashguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CrashDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crashRecord: CrashRecord)

    @Query("SELECT * FROM crash_logs ORDER BY timestamp DESC")
    suspend fun getAll(): List<CrashRecord>

    @Query("SELECT * FROM crash_logs ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<CrashRecord>>

    @Query("SELECT COUNT(*) FROM crash_logs")
    suspend fun getCount(): Int

    @Query("DELETE FROM crash_logs")
    suspend fun clearAll()
}
