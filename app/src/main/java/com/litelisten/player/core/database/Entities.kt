package com.litelisten.player.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val configJson: String,
    val credentialId: String?,
    val enabled: Boolean = true,
    val createdAt: Long,
    val lastConnectedAt: Long? = null,
    val lastSyncAt: Long? = null,
    val trackCount: Int = 0,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "tracks",
    indices = [
        Index("sourceId"), Index("album"), Index("artist"),
        Index("lastPlayedAt"), Index("addedAt"), Index("isFavorite"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val sourceType: String,
    val uri: String,
    val path: String,
    val title: String,
    val titleNorm: String,
    val artist: String?,
    val artistNorm: String?,
    val album: String?,
    val albumNorm: String?,
    val albumArtist: String?,
    val trackNo: Int?,
    val discNo: Int?,
    val durationMs: Long,
    val year: Int?,
    val genre: String?,
    val mimeType: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val channels: Int?,
    val bitDepth: Int?,
    val sizeBytes: Long?,
    val lastModified: Long?,
    val artworkKey: String?,
    val isFavorite: Boolean = false,
    val isAvailable: Boolean = true,
    val addedAt: Long,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0,
)

/**
 * 契约保留的 FTS4 外部内容表定义，当前**未注册进 AppDatabase**、数据库中不建此表。
 * 原因：FTS4 external content 要求内容表用 INTEGER rowid，而 TrackEntity.id 是 String，
 * 注册会直接编译失败；强行改写主键会波及全部 DAO 与播放队列。
 * 实际搜索走 norm 列的 LIKE：中文 / 全拼 / 首字母已拼进同一列，子串即可命中，
 * 且规避了 MATCH 语法注入面。文件名前缀搜索的性能优化留待后续单独处理。
 */
@Fts4(contentEntity = TrackEntity::class)
@Entity(tableName = "tracks_fts")
data class TrackFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val titleNorm: String,
    val artistNorm: String?,
    val albumNorm: String?,
    val path: String,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long,
)

data class AlbumRow(
    val name: String,
    val artist: String?,
    val artworkKey: String?,
    val trackCount: Int,
    val year: Int?,
)

data class ArtistRow(
    val name: String,
    val artworkKey: String?,
    val albumCount: Int,
    val trackCount: Int,
)
