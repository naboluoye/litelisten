package com.litelisten.player.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** P1：提供 AppDatabase / Dao / FTS 索引。此处先占位，避免后续改动注入图结构。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule
