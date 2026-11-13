package com.litelisten.player.di

import com.litelisten.player.core.datastore.SettingsRepository
import com.litelisten.player.core.datastore.SettingsRepositoryImpl
import com.litelisten.player.core.model.PlaybackController
import com.litelisten.player.core.player.PlaybackControllerImpl
import com.litelisten.player.core.source.api.CredentialStore
import com.litelisten.player.core.source.api.CredentialStoreImpl
import com.litelisten.player.data.repository.SearchRepository
import com.litelisten.player.data.repository.SearchRepositoryImpl
import com.litelisten.player.data.repository.SourceRepository
import com.litelisten.player.data.repository.SourceRepositoryImpl
import com.litelisten.player.data.repository.TrackRepository
import com.litelisten.player.data.repository.TrackRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindCredentialStore(impl: CredentialStoreImpl): CredentialStore

    @Binds @Singleton
    abstract fun bindTrackRepository(impl: TrackRepositoryImpl): TrackRepository

    @Binds @Singleton
    abstract fun bindSourceRepository(impl: SourceRepositoryImpl): SourceRepository

    @Binds @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds @Singleton
    abstract fun bindPlaybackController(impl: PlaybackControllerImpl): PlaybackController
}
