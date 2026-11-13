package com.litelisten.player.data.repository

import com.litelisten.player.core.model.Credentials
import com.litelisten.player.core.model.DiscoveredDevice
import com.litelisten.player.core.model.RemoteEntry
import com.litelisten.player.core.model.Source
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceInfo
import kotlinx.coroutines.flow.Flow

/** P3/P4 实现。 */
interface SourceRepository {
    fun sources(): Flow<List<Source>>
    suspend fun add(config: SourceConfig, credentials: Credentials?): Result<Source>
    suspend fun update(config: SourceConfig)
    suspend fun remove(sourceId: String)
    suspend fun test(config: SourceConfig, credentials: Credentials?): Result<SourceInfo>
    suspend fun browse(sourceId: String, path: String?): Result<List<RemoteEntry>>
    suspend fun scanIntoLibrary(sourceId: String, path: String): Result<Int>
    suspend fun discovered(): Flow<List<DiscoveredDevice>>
}
