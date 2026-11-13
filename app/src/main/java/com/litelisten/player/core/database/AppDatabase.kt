package com.litelisten.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SourceEntity::class,
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun sourceDao(): SourceDao
}
