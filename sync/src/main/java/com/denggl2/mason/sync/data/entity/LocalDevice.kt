package com.denggl2.mason.sync.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_device",
    indices = [Index(value = ["device_id"], unique = true)],
)
data class LocalDevice(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
