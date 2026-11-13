package com.litelisten.player.core.source.smb

import android.net.Uri
import androidx.media3.datasource.DataSource
import com.litelisten.player.core.common.MimeTypes
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** SMB2/3 目录浏览；流播走 SmbDataSource 的随机读，不整文件下载。 */
@Singleton
class SmbSourceProvider @Inject constructor(
    private val pool: SmbConnectionPool,
) : SourceProvider {
    override val type: SourceType = SourceType.SMB

    override suspend fun testConnection(config: SourceConfig): Result<SourceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val count = pool.useShare(config.id) { share, cfg ->
                runCatching { share.list(root(cfg)).size }.getOrDefault(0)
            }
            SourceInfo(config.name, "SMB2 · $count items")
        }
    }

    /** smbj 0.14.0 不暴露共享枚举 RPC；向导允许手动输入共享名。 */
    override suspend fun listShares(config: SourceConfig): Result<List<ShareInfo>> =
        Result.success(emptyList())

    override suspend fun browse(config: SourceConfig, path: String?): Result<List<RemoteEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val relative = path?.trim('/').orEmpty()
                val prefix = if (relative.isBlank()) "" else "$relative/"
                val entries = pool.useShare(config.id) { share, cfg ->
                    val target = listOf(root(cfg).trim('\\'), relative.replace('/', '\\'))
                        .filter { it.isNotBlank() }
                        .joinToString("\\")
                    share.list(target).mapNotNull { info ->
                        val name = info.fileName.orEmpty()
                        if (name.isBlank() || name == "." || name == "..") return@mapNotNull null
                        val directory = (info.fileAttributes and 0x10L) != 0L
                        RemoteEntry(
                            name = name,
                            path = "/$prefix$name",
                            isDirectory = directory,
                            sizeBytes = if (directory) null else info.endOfFile,
                            mimeType = if (directory) null else MimeTypes.fromPath(name),
                        )
                    }
                }
                val collator = java.text.Collator.getInstance(Locale.CHINA)
                entries.sortedWith(
                    compareByDescending<RemoteEntry> { it.isDirectory }
                        .thenComparator { a, b -> collator.compare(a.name, b.name) },
                ).take(200)
            }
        }

    override suspend fun search(config: SourceConfig, query: String, limit: Int): Result<List<Track>> =
        Result.failure(SourceError.Unsupported("SMB_SEARCH"))

    override suspend fun resolvePlayable(config: SourceConfig, track: Track): Result<PlayableMedia> =
        runCatching {
            val uri = if (track.uri.startsWith("smb://")) {
                track.uri
            } else {
                "smb://${config.id}/${Uri.encode(track.path.trimStart('/'), "/")}"
            }
            PlayableMedia(uri = uri, mimeType = track.mimeType)
        }

    override fun dataSourceFactory(config: SourceConfig): DataSource.Factory = SmbDataSourceFactory(pool)

    override suspend fun close(configId: String) = pool.close(configId)

    private fun root(config: SourceConfig): String =
        config.basePath?.trim('/')?.replace('/', '\\').orEmpty()
}
