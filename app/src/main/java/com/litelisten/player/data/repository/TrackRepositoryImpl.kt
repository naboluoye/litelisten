package com.litelisten.player.data.repository

import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.litelisten.player.core.database.AlbumRow
import com.litelisten.player.core.database.ArtistRow
import com.litelisten.player.core.database.TrackDao
import com.litelisten.player.core.database.toModel
import com.litelisten.player.core.model.Album
import com.litelisten.player.core.model.Artist
import com.litelisten.player.core.model.SortKey
import com.litelisten.player.core.model.SortOrder
import com.litelisten.player.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val dao: TrackDao,
) : TrackRepository {
    override fun tracks(sort: SortKey, order: SortOrder, sourceId: String?): Flow<PagingData<Track>> =
        allTracks(sort, order, sourceId).map { PagingData.from(it) }

    /** 列表走 Room Flow + LazyColumn(key=id)；排序片段只来自枚举，不拼接用户输入。 */
    fun allTracks(sort: SortKey, order: SortOrder, sourceId: String?): Flow<List<Track>> =
        dao.observeSorted(buildQuery(sort, order, sourceId))
            .map { rows -> rows.map { it.toModel() } }

    override fun albums(): Flow<List<Album>> = dao.albums().map { rows: List<AlbumRow> ->
        rows.map { it.toModel() }
    }

    override fun artists(): Flow<List<Artist>> = dao.artists().map { rows: List<ArtistRow> ->
        rows.map { it.toModel() }
    }

    fun folders(): Flow<List<String>> = dao.allPaths().map { paths ->
        paths.mapNotNull { path ->
            val normalized = path.replace('\\', '/')
            val first = normalized.trim('/').substringBefore('/')
            first.takeIf { it.isNotBlank() }
        }.distinct().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    fun byFolder(prefix: String): Flow<List<Track>> =
        dao.byFolder(prefix).map { rows -> rows.map { it.toModel() } }

    fun byAlbum(name: String): Flow<List<Track>> =
        dao.byAlbum(name).map { rows -> rows.map { it.toModel() } }

    fun byArtist(name: String): Flow<List<Track>> =
        dao.byArtist(name).map { rows -> rows.map { it.toModel() } }

    fun count(): Flow<Int> = dao.count()

    suspend fun search(query: String, limit: Int = 100): List<Track> =
        dao.search("%$query%", limit).map { it.toModel() }

    suspend fun byId(id: String): Track? = dao.byId(id)?.toModel()

    suspend fun byIds(ids: List<String>): List<Track> = dao.byIds(ids).map { it.toModel() }

    override suspend fun track(id: String): Track? = byId(id)

    override suspend fun markPlayed(id: String) = dao.markPlayed(id, System.currentTimeMillis())

    override suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    override suspend fun removeFromLibrary(id: String) = dao.deleteById(id)

    override fun recentlyPlayed(limit: Int): Flow<List<Track>> =
        dao.recentlyPlayed(limit).map { rows -> rows.map { it.toModel() } }

    override fun favorites(): Flow<List<Track>> =
        dao.favorites().map { rows -> rows.map { it.toModel() } }

    suspend fun clearAll() = dao.deleteAll()

    suspend fun clearSource(sourceId: String) = dao.deleteBySource(sourceId)

    private fun buildQuery(sort: SortKey, order: SortOrder, sourceId: String?): SimpleSQLiteQuery {
        val column = when (sort) {
            SortKey.TITLE -> "title COLLATE NOCASE"
            SortKey.ARTIST -> "artist COLLATE NOCASE"
            SortKey.ALBUM -> "album COLLATE NOCASE"
            SortKey.DURATION -> "durationMs"
            SortKey.DATE_ADDED -> "addedAt"
            SortKey.LAST_PLAYED -> "lastPlayedAt"
        }
        val direction = if (order == SortOrder.ASC) "ASC" else "DESC"
        return if (sourceId.isNullOrBlank()) {
            SimpleSQLiteQuery("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY $column $direction")
        } else {
            SimpleSQLiteQuery(
                "SELECT * FROM tracks WHERE isAvailable = 1 AND sourceId = ? ORDER BY $column $direction",
                arrayOf<Any>(sourceId),
            )
        }
    }
}
