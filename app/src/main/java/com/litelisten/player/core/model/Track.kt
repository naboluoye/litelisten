package com.litelisten.player.core.model

/** 统一曲目模型：本地与网络共用。id 规则见《05 契约》§2，不可随意改。 */
data class Track(
    val id: String,
    val sourceId: String,
    val sourceType: SourceType,
    val uri: String,
    val path: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val durationMs: Long = 0L,
    val year: Int? = null,
    val genre: String? = null,
    val mimeType: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val bitDepth: Int? = null,
    val sizeBytes: Long? = null,
    val lastModified: Long? = null,
    val artworkKey: String? = null,
    val isFavorite: Boolean = false,
    val isAvailable: Boolean = true,
    val addedAt: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
) {
    val displayArtist: String get() = artist?.takeIf { it.isNotBlank() } ?: "未知艺术家"
    val displayAlbum: String get() = album?.takeIf { it.isNotBlank() } ?: "未知专辑"
}

data class Album(
    val key: String,
    val name: String,
    val artist: String?,
    val artworkKey: String?,
    val trackCount: Int,
    val year: Int? = null,
)

data class Artist(
    val key: String,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    val artworkKey: String? = null,
)
