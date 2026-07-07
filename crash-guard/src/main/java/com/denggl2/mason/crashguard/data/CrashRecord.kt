package com.denggl2.mason.crashguard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crash_logs")
data class CrashRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "thread_name")
    val threadName: String,

    @ColumnInfo(name = "exception_type")
    val exceptionType: String,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "stack_trace")
    val stackTrace: String,

    @ColumnInfo(name = "app_version")
    val appVersion: String,

    @ColumnInfo(name = "is_launch_crash")
    val isLaunchCrash: Boolean,
)
