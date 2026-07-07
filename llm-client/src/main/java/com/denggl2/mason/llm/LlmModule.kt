package com.denggl2.mason.llm

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideChatClient(streamProcessor: StreamProcessor): ChatClient {
        return ChatClient(streamProcessor)
    }
}
