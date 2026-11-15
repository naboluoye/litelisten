package com.litelisten.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SourceEntity::class,
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        LyricsEntity::class,
        ManualLyricsEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun sourceDao(): SourceDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        /** v2：本地曲库增加 ReplayGain 列与内嵌歌词表；保留用户数据，不重建。 */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN replayGainDb REAL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS lyrics (" +
                        "trackId TEXT NOT NULL, " +
                        "isSynced INTEGER NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "PRIMARY KEY(trackId)" +
                        ")"
                )
            }
        }

        /** v3：用户手动挂载/批量导入的歌词单独建表，避免被重新扫描覆盖。 */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS manual_lyrics (" +
                        "trackId TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(trackId)" +
                        ")"
                )
            }
        }
    }
}
