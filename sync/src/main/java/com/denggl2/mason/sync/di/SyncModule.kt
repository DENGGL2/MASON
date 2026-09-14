package com.denggl2.mason.sync.di

import android.content.Context
import androidx.room.Room
import com.denggl2.mason.sync.data.AppDatabase
import com.denggl2.mason.sync.data.MIGRATION_1_2
import com.denggl2.mason.sync.data.dao.ConversationDao
import com.denggl2.mason.sync.data.dao.LocalDeviceDao
import com.denggl2.mason.sync.data.dao.MessageDao
import com.denggl2.mason.sync.data.dao.SyncCursorDao
import com.denggl2.mason.sync.data.dao.SyncOutboxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mason_database.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideLocalDeviceDao(database: AppDatabase): LocalDeviceDao = database.localDeviceDao()

    @Provides
    fun provideSyncOutboxDao(database: AppDatabase): SyncOutboxDao = database.syncOutboxDao()

    @Provides
    fun provideSyncCursorDao(database: AppDatabase): SyncCursorDao = database.syncCursorDao()
}
