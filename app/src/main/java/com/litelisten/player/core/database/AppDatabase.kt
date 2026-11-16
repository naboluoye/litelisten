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
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun sourceDao(): SourceDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun playlistDao(): PlaylistDao

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

        /**
         * v4：歌单条目增加曲目快照列。曲目记录被重新扫描 / 来源移除而暂时对不上时，
         * 用快照继续显示与播放，避免歌单里的歌「凭空消失」。全部带默认值，老数据直接可用。
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN snapTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN snapArtist TEXT")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN snapAlbum TEXT")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN snapUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN snapPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN snapSourceType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN snapSourceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN snapDurationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN snapMimeType TEXT")
            }
        }
    }
}
