package com.litelisten.player.core.source.jellyfin

import androidx.media3.datasource.DataSource
import com.litelisten.player.core.model.PlayableMedia
import com.litelisten.player.core.model.RemoteEntry
import com.litelisten.player.core.model.ShareInfo
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceError
import com.litelisten.player.core.model.SourceInfo
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.core.model.Track
import com.litelisten.player.core.source.api.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Jellyfin 媒体库浏览；播放走 http(s) 原始文件流 + OkHttp Range。 */
@Singleton
class JellyfinSourceProvider @Inject constructor(
    private val api: JellyfinClient,
) : SourceProvider {
    override val type: SourceType = SourceType.JELLYFIN

    override suspend fun testConnection(config: SourceConfig): Result<SourceInfo> =
        withContext(Dispatchers.IO) { runCatching { api.serverInfo(config) } }

    override suspend fun listShares(config: SourceConfig): Result<List<ShareInfo>> =
        Result.success(emptyList())

    override suspend fun browse(config: SourceConfig, path: String?): Result<List<RemoteEntry>> =
        withContext(Dispatchers.IO) { runCatching { api.browse(config, path) } }

    // 曲目加入曲库后走本地索引搜索，与 SMB / WebDAV 保持一致。
    override suspend fun search(config: SourceConfig, query: String, limit: Int): Result<List<Track>> =
        Result.failure(SourceError.Unsupported("JELLYFIN_SEARCH"))

    override suspend fun resolvePlayable(config: SourceConfig, track: Track): Result<PlayableMedia> =
        runCatching { PlayableMedia(uri = track.uri, mimeType = track.mimeType) }

    override fun dataSourceFactory(config: SourceConfig): DataSource.Factory? = null

    override suspend fun close(configId: String) = api.close(configId)
}
