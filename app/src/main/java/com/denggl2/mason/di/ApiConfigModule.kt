package com.denggl2.mason.di

import android.content.Context
import com.denggl2.mason.data.ApiConfigDataStore
import com.denggl2.mason.data.AiProviderCatalog
import com.denggl2.mason.data.LocalModelStore
import com.denggl2.mason.data.connection
import com.denggl2.mason.data.resolvedChatModelRef
import com.denggl2.mason.llm.ApiConfigProvider
import com.denggl2.mason.llm.LiteRtModelEngine
import com.denggl2.mason.llm.ResolvedApiConfig
import com.denggl2.mason.model.LlamaCppModelEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
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

            override suspend fun resolve(connectionId: String?): ResolvedApiConfig {
                val config = store.config.first()
                val selectedRef = config.resolvedChatModelRef()
                val selected = config.connection(connectionId ?: selectedRef.connectionId)
                if (selected == null) return super<ApiConfigProvider>.resolve(connectionId)
                val model = selected.modelIds.firstOrNull().orEmpty()
                return ResolvedApiConfig(
                    apiUrl = selected.apiUrl,
                    apiKey = selected.apiKey,
                    model = model,
                    toolsEnabled = selected.toolsSupported,
                    requiresApiKey = AiProviderCatalog.requiresApiKey(
                        selected.providerId,
                        selected.apiUrl,
                        model,
                    ),
                    additionalHeaders = buildMap {
                        if (selected.workspaceId.isNotBlank()) {
                            put("X-DashScope-WorkSpace", selected.workspaceId)
                        }
                    },
                )
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

    @Provides
    @Singleton
    fun provideLlamaCppModelEngine(
        @ApplicationContext context: Context,
        localModelStore: LocalModelStore,
    ): LlamaCppModelEngine = LlamaCppModelEngine(
        context = context,
        modelPathProvider = { modelId -> localModelStore.readyModelPath(modelId) },
    )
}
