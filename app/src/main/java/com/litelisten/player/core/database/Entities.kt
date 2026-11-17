package com.litelisten.player.core.database

import androidx.room.Entity
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
    /** ReplayGain 轨道增益（dB，多为负值）；无标签时为 null。 */
    val replayGainDb: Double? = null,
)

/** 扫描时从文件内嵌标签提取的歌词；一条曲目至多一条记录。 */
@Entity(tableName = "lyrics", primaryKeys = ["trackId"])
data class LyricsEntity(
    val trackId: String,
    val isSynced: Boolean,
    val content: String,
)

/**
 * 用户自己挂载或批量导入的歌词原文。与 [LyricsEntity] 分表存放，
 * 这样重新扫描曲库时不会把手动歌词当成内嵌歌词覆盖掉。
 */
@Entity(tableName = "manual_lyrics", primaryKeys = ["trackId"])
data class ManualLyricsEntity(
    val trackId: String,
    val content: String,
    val updatedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * 歌单条目：除曲目 id 外冗余保存一份入歌单时的关键信息快照。
 * 曲目记录因重新扫描 / 来源移除而暂时对不上时，用快照继续显示与播放，
 * 避免歌单里的歌「凭空消失」。老数据这些字段为空，读取时自动跳过。
 */
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long,
    val snapTitle: String = "",
    val snapArtist: String? = null,
    val snapAlbum: String? = null,
    val snapUri: String = "",
    val snapPath: String = "",
    val snapSourceType: String = "",
    val snapSourceId: String = "",
    val snapDurationMs: Long = 0L,
    val snapMimeType: String? = null,
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
