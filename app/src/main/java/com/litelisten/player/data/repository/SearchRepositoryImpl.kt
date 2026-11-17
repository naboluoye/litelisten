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
        // 多词搜索取交集：用 Set 的 id 求交替代 O(n×m) 的 list.any 线性扫描；
        // 只查询一次每个 token，首个结果保留顺序用于展示。
        var firstHits: List<Track> = emptyList()
        var commonIds: Set<String>? = null
        tokens.forEach { token ->
            val hits = tracks.search(token, 200)
            if (commonIds == null) firstHits = hits
            val ids = hits.mapTo(HashSet(hits.size)) { it.id }
            commonIds = if (commonIds == null) ids else commonIds!! intersect ids
        }
        val finalIds = commonIds ?: emptySet()
        val local: List<Track> = firstHits.filter { it.id in finalIds }

        val lowered = trimmed.lowercase()
        // normalize 含拼音转换，预计算一次，别在 filter 谓词里对每个 token 重复算。
        fun matches(name: String): Boolean {
            val norm = Pinyin.normalize(name)
            return name.lowercase().contains(lowered) || tokens.any { norm.contains(it) }
        }
        val albums: List<Album> = runCatching { tracks.albums().first() }.getOrDefault(emptyList())
            .filter { matches(it.name) }
            .take(20)
        val artists: List<Artist> = runCatching { tracks.artists().first() }.getOrDefault(emptyList())
            .filter { matches(it.name) }
            .take(20)
        emit(SearchResult(localTracks = local.take(100), localAlbums = albums, localArtists = artists))
    }
}
