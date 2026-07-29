package com.denggl2.mason.sync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.denggl2.mason.sync.data.entity.LocalDevice

@Dao
interface LocalDeviceDao {
    @Query("SELECT * FROM local_device WHERE singleton_id = 1")
    suspend fun get(): LocalDevice?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(device: LocalDevice): Long
}
