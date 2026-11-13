package com.litelisten.player.di

import android.content.Context
import androidx.room.Room
import com.litelisten.player.core.database.AppDatabase
import com.litelisten.player.core.database.SourceDao
import com.litelisten.player.core.database.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "litelisten.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideSourceDao(db: AppDatabase): SourceDao = db.sourceDao()
}
