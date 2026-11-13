package com.litelisten.player.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** P2：提供 ExoPlayer / MediaSession / PlaybackController。 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule
