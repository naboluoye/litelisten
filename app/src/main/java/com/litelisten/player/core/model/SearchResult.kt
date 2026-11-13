package com.litelisten.player.core.model

/** 本地即时结果 + 网络增量结果；networkPending 用来显示"正在查询"。 */
data class SearchResult(
    val localTracks: List<Track> = emptyList(),
    val localAlbums: List<Album> = emptyList(),
    val localArtists: List<Artist> = emptyList(),
    val networkTracks: List<Track> = emptyList(),
    val networkPending: Set<String> = emptySet(),
    val errors: List<String> = emptyList(),
)
