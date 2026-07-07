package com.denggl2.mason.crashguard.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CrashRecord::class],
    version = 1,
    exportSchema = false,
)
abstract class CrashDatabase : RoomDatabase() {
    abstract fun crashDao(): CrashDao
}
