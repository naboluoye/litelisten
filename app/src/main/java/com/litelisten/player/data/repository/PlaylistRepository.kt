package com.litelisten.player.data.repository

import android.content.Context
import com.litelisten.player.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.litelisten.player.core.database.PlaylistDao
import com.litelisten.player.core.database.PlaylistEntity
import com.litelisten.player.core.database.PlaylistTrackEntity
import com.litelisten.player.core.database.TrackDao
import com.litelisten.player.core.database.snapshotTrack
import com.litelisten.player.core.database.toModel
import com.litelisten.player.core.model.Playlist
import com.litelisten.player.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

/** 自建歌单：只维护「歌单 → 曲目 id + 顺序」，歌曲元数据仍以 tracks 表为准。 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
    private val trackDao: TrackDao,
    @ApplicationContext private val context: Context,
) {
    fun playlists(): Flow<List<Playlist>> = dao.observeAll().map { rows ->
        rows.map { Playlist(id = it.id, name = it.name, trackCount = it.trackCount, createdAt = it.createdAt) }
    }

    /**
     * 曲目按 playlist_tracks.position 排序。
     * 曲目记录暂时对不上（重新扫描 / 来源移除导致 id 变化）时，用条目里的快照还原，
     * 歌不会「凭空消失」；快照也为空的老数据才跳过。
     */
    fun tracks(playlistId: Long): Flow<List<Track>> =
        dao.observeEntries(playlistId).mapLatest { entries ->
            if (entries.isEmpty()) return@mapLatest emptyList()
            // 分批查：整夹加入可能上千首，单条 IN (...) 会超过 SQLite 的参数上限。
            val byId = entries.map { it.trackId }.chunked(400)
                .flatMap { ids -> trackDao.byIds(ids) }
                .associateBy { it.id }
            entries.mapNotNull { entry ->
                byId[entry.trackId]?.toModel() ?: entry.snapshotTrack()
            }
        }

    fun playlist(playlistId: Long): Flow<Playlist?> = dao.observePlaylist(playlistId).map { row ->
        row?.let { Playlist(id = it.id, name = it.name, trackCount = 0, createdAt = it.createdAt) }
    }

    suspend fun all(): List<Playlist> = dao.all().map {
        Playlist(id = it.id, name = it.name, trackCount = dao.countIn(it.id), createdAt = it.createdAt)
    }

    suspend fun create(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    /** 新建歌单并一次性放进多首歌（文件夹整体加入用）。 */
    suspend fun createWith(name: String, tracks: List<Track>): Long {
        val id = create(name)
        addTracks(id, tracks)
        return id
    }

    suspend fun rename(playlistId: Long, name: String) = dao.rename(playlistId, name.trim())

    suspend fun delete(playlistId: Long) {
        dao.clearEntries(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun removeTrack(playlistId: Long, trackId: String) = dao.removeEntry(playlistId, trackId)

    /** 已在歌单里的歌自动跳过，避免重复；返回真正新增的条数。 */
    suspend fun addTracks(playlistId: Long, tracks: List<Track>): Int {
        if (tracks.isEmpty()) return 0
        val existing = dao.trackIds(playlistId).toHashSet()
        var position = dao.maxPosition(playlistId)
        val now = System.currentTimeMillis()
        val rows = ArrayList<PlaylistTrackEntity>(tracks.size)
        tracks.forEach { track ->
            if (!existing.add(track.id)) return@forEach
            position += 1
            rows.add(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = position,
                    addedAt = now,
                    snapTitle = track.title,
                    snapArtist = track.artist,
                    snapAlbum = track.album,
                    snapUri = track.uri,
                    snapPath = track.path,
                    snapSourceType = track.sourceType.name,
                    snapSourceId = track.sourceId,
                    snapDurationMs = track.durationMs,
                    snapMimeType = track.mimeType,
                )
            )
        }
        if (rows.isNotEmpty()) dao.insertEntries(rows)
        return rows.size
    }

    /**
     * 一键收藏：确保存在一个固定名的「我喜欢的」歌单，把歌曲直接加进去。
     * 名字固定所以重复调用只会复用同一个歌单，不会每次新建。
     */
    suspend fun quickFavorite(tracks: List<Track>): Int {
        if (tracks.isEmpty()) return 0
        val name = context.getString(R.string.playlist_quick_name)
        val existing = dao.all().firstOrNull { it.name == name }
        val id = existing?.id ?: create(name)
        return addTracks(id, tracks)
    }

    suspend fun count(): Int = dao.count()

    /** 曲库重置时调用：歌单名字保留，里面的歌曲条目清空。 */
    suspend fun clearAllEntries() = dao.deleteAllEntries()

    suspend fun pruneOrphans() = dao.deleteOrphanEntries()
}
