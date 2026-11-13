package com.litelisten.player.data.repository

import com.litelisten.player.core.model.SearchResult
import kotlinx.coroutines.flow.Flow

/** P6 实现：本地即时 + 网络增量。 */
interface SearchRepository {
    fun search(query: String): Flow<SearchResult>
}
