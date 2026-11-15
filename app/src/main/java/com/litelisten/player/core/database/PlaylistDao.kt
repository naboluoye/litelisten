package com.litelisten.player.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 歌单列表的轻量投影：带上曲目数，避免把整张关联表读进内存。 */
data class PlaylistRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
)

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, " +
            "COUNT(t.trackId) AS trackCount FROM playlists p " +
            "LEFT JOIN playlist_tracks t ON t.playlistId = p.id " +
            "GROUP BY p.id, p.name, p.createdAt ORDER BY p.createdAt",
    )
    fun observeAll(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists ORDER BY createdAt")
    suspend fun all(): List<PlaylistEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun trackIds(playlistId: Long): List<String>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun countIn(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeEntry(playlistId: Long, trackId: String)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    /**
     * 曲库清空或曲目被移除后，清掉指向不存在曲目的残留条目。
     * 曲库为空时 NOT IN 空集合恒为真，等于全部清空，正是需要的行为。
     */
    @Query("DELETE FROM playlist_tracks WHERE trackId NOT IN (SELECT id FROM tracks)")
    suspend fun deleteOrphanEntries()

    @Query("DELETE FROM playlist_tracks")
    suspend fun deleteAllEntries()
}
