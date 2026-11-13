package com.litelisten.player.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @RawQuery(observedEntities = [TrackEntity::class])
    fun observeSorted(query: SupportSQLiteQuery): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND album = :album ORDER BY discNo, trackNo, title")
    fun byAlbum(album: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND artist = :artist ORDER BY album, trackNo, title")
    fun byArtist(artist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND path LIKE :prefix || '%' ORDER BY title COLLATE NOCASE")
    fun byFolder(prefix: String): Flow<List<TrackEntity>>

    @Query("SELECT path FROM tracks WHERE isAvailable = 1")
    fun allPaths(): Flow<List<String>>

    @Query(
        "SELECT * FROM tracks WHERE isAvailable = 1 AND (" +
            "titleNorm LIKE :q OR artistNorm LIKE :q OR albumNorm LIKE :q OR path LIKE :q) " +
            "ORDER BY title COLLATE NOCASE LIMIT :limit"
    )
    suspend fun search(q: String, limit: Int): List<TrackEntity>

    @Query(
        "SELECT album AS name, albumArtist AS artist, MIN(artworkKey) AS artworkKey, " +
            "COUNT(*) AS trackCount, MAX(year) AS year FROM tracks " +
            "WHERE isAvailable = 1 AND album IS NOT NULL AND album <> '' " +
            "GROUP BY album, albumArtist ORDER BY album COLLATE NOCASE"
    )
    fun albums(): Flow<List<AlbumRow>>

    @Query(
        "SELECT artist AS name, MIN(artworkKey) AS artworkKey, " +
            "COUNT(DISTINCT album) AS albumCount, COUNT(*) AS trackCount FROM tracks " +
            "WHERE isAvailable = 1 AND artist IS NOT NULL AND artist <> '' " +
            "GROUP BY artist ORDER BY artist COLLATE NOCASE"
    )
    fun artists(): Flow<List<ArtistRow>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND isFavorite = 1 ORDER BY title COLLATE NOCASE")
    fun favorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun recentlyPlayed(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE isAvailable = 1")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE sourceId = 'local' AND isAvailable = 1")
    suspend fun countLocal(): Int

    @Upsert
    suspend fun upsertAll(items: List<TrackEntity>)

    @Transaction
    @Query("DELETE FROM tracks WHERE sourceId = :sourceId AND id NOT IN (:keepIds)")
    suspend fun prune(sourceId: String, keepIds: List<String>)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :ts WHERE id = :id")
    suspend fun markPlayed(id: String, ts: Long)

    @Query("UPDATE tracks SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE tracks SET isAvailable = 0 WHERE id = :id")
    suspend fun markUnavailable(id: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tracks WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()
}
