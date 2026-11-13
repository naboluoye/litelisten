package com.litelisten.player.data.repository

import com.litelisten.player.core.common.Pinyin
import com.litelisten.player.core.model.Album
import com.litelisten.player.core.model.Artist
import com.litelisten.player.core.model.SearchResult
import com.litelisten.player.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** 本地结果先出（norm 列 LIKE 覆盖中文/全拼/首字母），网络源随后增量。 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val tracks: TrackRepositoryImpl,
) : SearchRepository {
    override fun search(query: String): Flow<SearchResult> = flow {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            emit(SearchResult())
            return@flow
        }
        val tokens = Pinyin.searchTokens(trimmed)
        var local: List<Track> = emptyList()
        tokens.forEachIndexed { index, token ->
            val hits = tracks.search(token, 200)
            local = if (index == 0) hits else local.filter { candidate -> hits.any { it.id == candidate.id } }
        }
        val lowered = trimmed.lowercase()
        val albums: List<Album> = runCatching { tracks.albums().first() }.getOrDefault(emptyList())
            .filter { it.name.lowercase().contains(lowered) || tokens.any { t -> Pinyin.normalize(it.name).contains(t) } }
            .take(20)
        val artists: List<Artist> = runCatching { tracks.artists().first() }.getOrDefault(emptyList())
            .filter { it.name.lowercase().contains(lowered) || tokens.any { t -> Pinyin.normalize(it.name).contains(t) } }
            .take(20)
        emit(SearchResult(localTracks = local.take(100), localAlbums = albums, localArtists = artists))
    }
}
