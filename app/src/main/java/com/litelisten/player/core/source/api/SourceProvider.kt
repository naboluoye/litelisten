package com.litelisten.player.core.source.api

import androidx.media3.datasource.DataSource
import com.litelisten.player.core.model.PlayableMedia
import com.litelisten.player.core.model.RemoteEntry
import com.litelisten.player.core.model.ShareInfo
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceInfo
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.core.model.Track

/** 协议可插拔的唯一入口；新增协议 = 加一个实现 + 一个枚举值 + 一个表单页。 */
interface SourceProvider {
    val type: SourceType

    suspend fun testConnection(config: SourceConfig): Result<SourceInfo>
    suspend fun listShares(config: SourceConfig): Result<List<ShareInfo>>
    suspend fun browse(config: SourceConfig, path: String?): Result<List<RemoteEntry>>
    suspend fun search(config: SourceConfig, query: String, limit: Int): Result<List<Track>>
    suspend fun resolvePlayable(config: SourceConfig, track: Track): Result<PlayableMedia>
    fun dataSourceFactory(config: SourceConfig): DataSource.Factory?
    suspend fun close(configId: String)
}
