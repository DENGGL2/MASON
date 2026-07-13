package com.denggl2.mason.di

import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.LocalModelStore
import com.denggl2.mason.llm.ApiConfigProvider
import com.denggl2.mason.llm.LiteRtModelEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiConfigModule {

    @Provides
    @Singleton
    fun provideApiConfigProvider(store: ApiConfigDataStore): ApiConfigProvider {
        return object : ApiConfigProvider {
            override suspend fun getApiUrl(): String {
                return store.config.first().apiUrl
            }

            override suspend fun getApiKey(): String {
                return store.config.first().apiKey
            }

            override suspend fun getModel(): String {
                return store.config.first().model
            }

            override suspend fun getToolsEnabled(): Boolean {
                return store.config.first().toolsEnabled
            }

            override suspend fun requiresApiKey(): Boolean {
                return AiProviderCatalog.requiresApiKey(store.config.first())
            }
        }
    }

    @Provides
    @Singleton
    fun provideLiteRtModelEngine(localModelStore: LocalModelStore): LiteRtModelEngine {
        return LiteRtModelEngine(
            modelPathProvider = { modelId -> localModelStore.readyModelPath(modelId) },
            cacheDirProvider = { localModelStore.inferenceCacheDir() },
        )
    }
}
