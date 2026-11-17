package com.litelisten.player.data.repository

import com.litelisten.player.core.model.Album
import com.litelisten.player.core.model.Artist
import com.litelisten.player.core.model.Track
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun albums(): Flow<List<Album>>
    fun artists(): Flow<List<Artist>>
    suspend fun track(id: String): Track?
    suspend fun markPlayed(id: String)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun removeFromLibrary(id: String)
    fun recentlyPlayed(limit: Int): Flow<List<Track>>
    fun favorites(): Flow<List<Track>>
}
