package com.denggl2.mason.crashguard.di

import android.content.Context
import androidx.room.Room
import com.denggl2.mason.crashguard.data.CrashDao
import com.denggl2.mason.crashguard.data.CrashDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CrashGuardModule {

    @Provides
    @Singleton
    fun provideCrashDatabase(@ApplicationContext context: Context): CrashDatabase {
        return Room.databaseBuilder(
            context,
            CrashDatabase::class.java,
            "mason_crash_database.db",
        ).build()
    }

    @Provides
    fun provideCrashDao(database: CrashDatabase): CrashDao {
        return database.crashDao()
    }
}
