package com.litelisten.player.core.model

/** 用户自建歌单。曲目顺序由 playlist_tracks.position 决定。 */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val createdAt: Long = 0L,
)
