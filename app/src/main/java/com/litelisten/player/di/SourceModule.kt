package com.litelisten.player.di

import com.litelisten.player.core.source.api.SourceRegistry
import com.litelisten.player.core.source.dlna.DlnaSourceProvider
import com.litelisten.player.core.source.jellyfin.JellyfinSourceProvider
import com.litelisten.player.core.source.smb.SmbSourceProvider
import com.litelisten.player.core.source.webdav.WebDavSourceProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {
    @Provides
    @Singleton
    fun provideSourceRegistry(
        smb: SmbSourceProvider,
        webDav: WebDavSourceProvider,
        dlna: DlnaSourceProvider,
        jellyfin: JellyfinSourceProvider,
    ): SourceRegistry = SourceRegistry().apply {
        register(smb)
        register(webDav)
        register(dlna)
        register(jellyfin)
    }
}
