package com.litelisten.player.core.source.webdav

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

/** WebDAV 目录浏览；播放直接走 http(s) + OkHttp Range。 */
@Singleton
class WebDavSourceProvider @Inject constructor(
    private val webDav: WebDavClient,
) : SourceProvider {
    override val type: SourceType = SourceType.WEBDAV

    override suspend fun testConnection(config: SourceConfig): Result<SourceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            webDav.propfind(config, config.basePath, 0)
            SourceInfo(config.name, "WebDAV")
        }
    }

    override suspend fun listShares(config: SourceConfig): Result<List<ShareInfo>> =
        Result.success(emptyList())

    override suspend fun browse(config: SourceConfig, path: String?): Result<List<RemoteEntry>> =
        withContext(Dispatchers.IO) {
            runCatching { webDav.propfind(config, path ?: config.basePath, 1) }
        }

    override suspend fun search(config: SourceConfig, query: String, limit: Int): Result<List<Track>> =
        Result.failure(SourceError.Unsupported("WEBDAV_SEARCH"))

    override suspend fun resolvePlayable(config: SourceConfig, track: Track): Result<PlayableMedia> =
        runCatching { PlayableMedia(uri = track.uri, mimeType = track.mimeType) }

    override fun dataSourceFactory(config: SourceConfig): DataSource.Factory? = null

    override suspend fun close(configId: String) = Unit
}
