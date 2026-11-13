package com.litelisten.player.data.repository

import androidx.paging.PagingData
import com.litelisten.player.core.model.Album
import com.litelisten.player.core.model.Artist
import com.litelisten.player.core.model.SortKey
import com.litelisten.player.core.model.SortOrder
import com.litelisten.player.core.model.Track
import kotlinx.coroutines.flow.Flow

/** P1 实现。P0 只冻结签名，不生成会抛 NotImplementedError 的空实现类。 */
interface TrackRepository {
    fun tracks(sort: SortKey, order: SortOrder, sourceId: String?): Flow<PagingData<Track>>
    fun albums(): Flow<List<Album>>
    fun artists(): Flow<List<Artist>>
    suspend fun track(id: String): Track?
    suspend fun markPlayed(id: String)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun removeFromLibrary(id: String)
    fun recentlyPlayed(limit: Int): Flow<List<Track>>
    fun favorites(): Flow<List<Track>>
}
