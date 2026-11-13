package com.litelisten.player.di

import com.litelisten.player.core.network.OkHttpProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideLanOkHttpClient(provider: OkHttpProvider): OkHttpClient = provider.base()

    @Provides
    @Singleton
    @Named("streaming")
    fun provideStreamingClient(provider: OkHttpProvider): OkHttpClient = provider.streaming()
}
