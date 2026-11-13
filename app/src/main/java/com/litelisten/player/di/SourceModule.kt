package com.litelisten.player.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** P3/P4：把 Local / SMB / WebDAV / DLNA 实现注册进 SourceRegistry。 */
@Module
@InstallIn(SingletonComponent::class)
object SourceModule
