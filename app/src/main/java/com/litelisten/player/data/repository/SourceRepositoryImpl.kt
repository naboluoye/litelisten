package com.litelisten.player.data.repository

import android.net.Uri
import com.litelisten.player.core.common.MimeTypes
import com.litelisten.player.core.common.Pinyin
import com.litelisten.player.core.database.SourceDao
import com.litelisten.player.core.database.SourceEntity
import com.litelisten.player.core.database.TrackDao
import com.litelisten.player.core.database.TrackEntity
import com.litelisten.player.core.database.toModel
import com.litelisten.player.core.model.Credentials
import com.litelisten.player.core.model.DiscoveredDevice
import com.litelisten.player.core.model.RemoteEntry
import com.litelisten.player.core.model.Source
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceError
import com.litelisten.player.core.model.ShareInfo
import com.litelisten.player.core.model.SourceInfo
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.core.network.AuthInterceptor
import com.litelisten.player.core.scan.LibraryScanner
import com.litelisten.player.core.source.api.CredentialStore
import com.litelisten.player.core.source.api.SourceRegistry
import com.litelisten.player.core.source.discovery.LanHostScanner
import com.litelisten.player.core.source.dlna.DlnaDiscovery
import com.litelisten.player.core.source.smb.SmbConnectionPool
import com.litelisten.player.core.source.webdav.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val trackDao: TrackDao,
    private val credentialStore: CredentialStore,
    private val registry: SourceRegistry,
    private val smbPool: SmbConnectionPool,
    private val webDav: WebDavClient,
    private val auth: AuthInterceptor,
    private val dlnaDiscovery: DlnaDiscovery,
    private val lanHostScanner: LanHostScanner,
    private val libraryScanner: LibraryScanner,
) : SourceRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun sources(): Flow<List<Source>> = sourceDao.observeAll().map { rows ->
        rows.map { row ->
            val config = runCatching { json.decodeFromString<SourceConfig>(row.configJson) }.getOrNull()
            Source(
                id = row.id,
                type = runCatching { SourceType.valueOf(row.type) }.getOrDefault(SourceType.SMB),
                name = row.name,
                detail = describe(config),
                enabled = row.enabled,
                trackCount = row.trackCount,
                lastConnectedAt = row.lastConnectedAt,
            )
        }
    }

    suspend fun config(sourceId: String): SourceConfig? =
        sourceDao.byId(sourceId)?.let { runCatching { json.decodeFromString<SourceConfig>(it.configJson) }.getOrNull() }

    suspend fun configs(): List<SourceConfig> = sourceDao.observeAll().first().mapNotNull { row ->
        runCatching { json.decodeFromString<SourceConfig>(row.configJson) }.getOrNull()
    }

    /**
     * 冷启动恢复：SMB 连接池、WebDAV 认证头、DLNA 头都只存在内存里，
     * 杀进程后必须重新从数据库 + 加密凭据注册一次，否则曲库里的网络歌曲点开即失败。
     */
    suspend fun hydrate(): Int = withContext(Dispatchers.IO) {
        val all = configs()
        all.forEach { cfg -> runCatching { prepare(cfg, credentialStore.get(cfg.id)) } }
        all.size
    }

    override suspend fun add(config: SourceConfig, credentials: Credentials?): Result<Source> = withContext(Dispatchers.IO) {
        runCatching {
            credentialStore.put(config.id, credentials?.username, credentials?.password)
            prepare(config, credentials)
            sourceDao.upsert(
                SourceEntity(
                    id = config.id,
                    type = config.type.name,
                    name = config.name,
                    configJson = json.encodeToString(config),
                    credentialId = config.id.takeIf { credentials != null },
                    createdAt = System.currentTimeMillis(),
                ),
            )
            Source(config.id, config.type, config.name, describe(config))
        }
    }

    override suspend fun update(config: SourceConfig) = withContext(Dispatchers.IO) {
        val existing = sourceDao.byId(config.id)
        credentialStore.get(config.id)?.let { prepare(config, it) }
        sourceDao.upsert(
            (existing ?: SourceEntity(
                id = config.id, type = config.type.name, name = config.name,
                configJson = "", credentialId = null, createdAt = System.currentTimeMillis(),
            )).copy(name = config.name, configJson = json.encodeToString(config)),
        )
        Unit
    }

    override suspend fun remove(sourceId: String) = withContext(Dispatchers.IO) {
        config(sourceId)?.let { cfg ->
            smbPool.forget(sourceId)
            runCatching { Uri.parse(cfg.url.orEmpty()) }.getOrNull()?.host?.let { host ->
                auth.clear(host, portOf(cfg))
            }
        }
        credentialStore.remove(sourceId)
        trackDao.deleteBySource(sourceId)
        sourceDao.delete(sourceId)
        Unit
    }

    override suspend fun test(config: SourceConfig, credentials: Credentials?): Result<SourceInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                credentialStore.put(config.id, credentials?.username, credentials?.password)
                prepare(config, credentials)
                val provider = registry.get(config.type)
                    ?: throw SourceError.Unsupported(config.type.name)
                provider.testConnection(config).getOrThrow()
            }
        }

    /** 添加向导用：连上服务器后列出可用共享名，省得用户自己猜。 */
    suspend fun listShares(config: SourceConfig, credentials: Credentials?): Result<List<ShareInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                prepare(config, credentials)
                val provider = registry.get(config.type)
                    ?: throw SourceError.Unsupported(config.type.name)
                provider.listShares(config).getOrThrow()
            }
        }

    override suspend fun browse(sourceId: String, path: String?): Result<List<RemoteEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val config = config(sourceId) ?: throw SourceError.NotFound()
                prepare(config, credentialStore.get(sourceId))
                val provider = registry.get(config.type) ?: throw SourceError.Unsupported(config.type.name)
                //  provider 里抛出的普通异常（XML 解析、smbj 运行时异常等）以前会一路冒到
                // UI 变成「出了点问题」，连是哪一步失败都看不出来。统一带上异常类型。
                val entries = runCatching { provider.browse(config, path).getOrThrow() }
                    .getOrElse { error ->
                        throw if (error is SourceError) error
                        else SourceError.Protocol("BROWSE_${error.javaClass.simpleName}")
                    }
                sourceDao.updateConnected(sourceId, System.currentTimeMillis())
                entries
            }
        }

    override suspend fun scanIntoLibrary(sourceId: String, path: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val config = config(sourceId) ?: throw SourceError.NotFound()
            prepare(config, credentialStore.get(sourceId))
            val provider = registry.get(config.type) ?: throw SourceError.Unsupported(config.type.name)
            val files = ArrayList<RemoteEntry>()
            walk(provider, config, path, 0, files)
            val tracks = files.mapNotNull { entry -> buildTrack(config, entry) }
            val count = libraryScanner.importTracks(tracks)
            sourceDao.updateSync(sourceId, count, System.currentTimeMillis())
            count
        }
    }

    /**
     * 分两次 emit：先给 SSDP 找到的 DLNA 媒体服务器，再补上子网里
     * 开放 SMB / WebDAV 端口的电脑与 NAS。普通文件共享不响应 M-SEARCH，
     * 只扫 SSDP 会让「发现的设备」永远是空的。
     */
    override suspend fun discovered(): Flow<List<DiscoveredDevice>> = flow {
        val upnp = runCatching { dlnaDiscovery.discover() }.getOrDefault(emptyList())
        emit(upnp)
        val lan = runCatching { lanHostScanner.scan() }.getOrDefault(emptyList())
        emit((upnp + lan).distinctBy { it.id })
    }

    suspend fun playableTracks(sourceId: String, entries: List<RemoteEntry>): List<com.litelisten.player.core.model.Track> {
        val config = config(sourceId) ?: return emptyList()
        // 兜底：若冷启动的 hydrate() 尚未完成，播放前再注册一次。
        prepare(config, credentialStore.get(sourceId))
        return entries.mapNotNull { buildTrack(config, it)?.let(::mapTrack) }
    }

    private fun mapTrack(entity: TrackEntity): com.litelisten.player.core.model.Track = entity.toModel()

    private suspend fun walk(
        provider: com.litelisten.player.core.source.api.SourceProvider,
        config: SourceConfig,
        path: String,
        depth: Int,
        out: MutableList<RemoteEntry>,
    ) {
        if (depth > 8 || out.size >= 4000) return
        val children = provider.browse(config, path).getOrDefault(emptyList())
        children.forEach { entry ->
            if (entry.isDirectory) {
                walk(provider, config, entry.path, depth + 1, out)
            } else if (MimeTypes.isSupported(entry.name)) {
                out.add(entry)
            }
        }
    }

    private fun buildTrack(config: SourceConfig, entry: RemoteEntry): TrackEntity? {
        val title = entry.name.substringBeforeLast('.', entry.name).ifBlank { entry.name }
        val (id, uri, sourceType) = when (config.type) {
            SourceType.SMB -> Triple(
                "smb:${config.id}:${sha1(entry.path)}",
                "smb://${config.id}/${Uri.encode(entry.path.trimStart('/'), "/")}",
                SourceType.SMB,
            )
            SourceType.WEBDAV -> {
                val url = webDav.urlOf(config, entry.path)
                Triple("webdav:${config.id}:${sha1(url)}", url, SourceType.WEBDAV)
            }
            SourceType.DLNA -> Triple(
                "dlna:${config.id}:${entry.path}",
                entry.playableUri ?: return null,
                SourceType.DLNA,
            )
            else -> return null
        }
        return TrackEntity(
            id = id,
            sourceId = config.id,
            sourceType = sourceType.name,
            uri = uri,
            path = entry.path,
            title = title,
            titleNorm = Pinyin.normalize(title),
            artist = null,
            artistNorm = null,
            album = null,
            albumNorm = null,
            albumArtist = null,
            trackNo = null,
            discNo = null,
            durationMs = 0L,
            year = null,
            genre = null,
            mimeType = entry.mimeType ?: MimeTypes.fromPath(entry.name),
            bitrateKbps = null,
            sampleRateHz = null,
            channels = null,
            bitDepth = null,
            sizeBytes = entry.sizeBytes,
            lastModified = entry.lastModified,
            artworkKey = null,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun prepare(config: SourceConfig, credentials: Credentials?) {
        when (config.type) {
            SourceType.SMB -> smbPool.configure(config, credentials?.username, credentials?.password)
            SourceType.WEBDAV -> webDav.prepare(config, credentials?.username, credentials?.password)
            SourceType.DLNA -> {
                val url = runCatching { Uri.parse(config.controlUrl.orEmpty()) }.getOrNull()
                url?.host?.let { host ->
                    auth.registerHeaders(
                        host,
                        if (url.port > 0) url.port
                        else if (url.scheme.equals("https", true)) 443 else 80,
                        mapOf(
                            "transferMode.dlna.org" to "Streaming",
                            "getcontentFeatures.dlna.org" to "1",
                        ),
                    )
                }
            }
            else -> Unit
        }
    }

    /** 与 WebDavClient.prepare / DLNA registerHeaders 使用同一套默认端口，避免清不掉认证头。 */
    private fun portOf(config: SourceConfig): Int {
        if (config.type == SourceType.SMB) return config.port ?: 445
        val url = runCatching { Uri.parse(config.url.orEmpty()) }.getOrNull() ?: return 80
        if (url.port > 0) return url.port
        return if (url.scheme.equals("https", true)) 443 else 80
    }

    private fun describe(config: SourceConfig?): String = when (config?.type) {
        SourceType.SMB -> "${config.host.orEmpty()}/${config.share.orEmpty()}"
        SourceType.WEBDAV, SourceType.DLNA -> config.url.orEmpty()
        else -> ""
    }

    private fun sha1(input: String): String =
        MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
