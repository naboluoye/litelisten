package com.litelisten.player.core.source.jellyfin

import android.net.Uri
import com.litelisten.player.BuildConfig
import com.litelisten.player.core.common.MimeTypes
import com.litelisten.player.core.model.Credentials
import com.litelisten.player.core.model.RemoteEntry
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceError
import com.litelisten.player.core.model.SourceInfo
import com.litelisten.player.core.network.AuthInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jellyfin 原生 API 客户端。
 *
 * 与 SMB / WebDAV 不同，Jellyfin 不提供文件共享协议，只有自己的 REST 接口，
 * 因此必须单独实现：账号密码换 token -> 浏览媒体库 -> 取原始文件流。
 *
 * 播放地址一律用 /Audio/{id}/stream?static=true 拿服务器上的原文件（支持 Range），
 * 绝不走 /Audio/{id}/universal 这类可能触发服务端转码的端点，保证无损原音质。
 * 认证同时带 Authorization: MediaBrowser Token=... 与 X-Emby-Token：
 * Jellyfin 12 起数据路由只认前者，旧版本与部分兼容实现只认后者。
 * token 只通过 AuthInterceptor 按 host:port 注入请求头，不写进 URI、
 * 不落数据库、不进播放队列快照。
 */
@Singleton
class JellyfinClient @Inject constructor(
    private val client: OkHttpClient,
    private val auth: AuthInterceptor,
) {
    private data class Session(
        val baseUrl: String,
        val host: String,
        val port: Int,
        val userId: String,
        val token: String,
        val fingerprint: Int,
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    /** 每次访问前调用：凭据不变则复用内存会话，冷启动 hydrate 或改密码后自动重新登录。 */
    suspend fun prepare(config: SourceConfig, credentials: Credentials?) = withContext(Dispatchers.IO) {
        val base = baseUrl(config)
        val username = credentials?.username.orEmpty()
        val password = credentials?.password.orEmpty()
        val fingerprint = "$username\u0000$password".hashCode()
        val cached = sessions[config.id]
        if (cached != null && cached.baseUrl == base && cached.fingerprint == fingerprint &&
            cached.token.isNotBlank()
        ) {
            registerHeaders(cached)
            return@withContext
        }
        if (base.isBlank()) throw SourceError.Protocol("MissingHost")
        if (username.isBlank()) throw SourceError.AuthFailed()
        val session = login(base, username, password, fingerprint)
        sessions[config.id] = session
        registerHeaders(session)
    }

    suspend fun serverInfo(config: SourceConfig): SourceInfo = withContext(Dispatchers.IO) {
        val session = sessions[config.id] ?: throw SourceError.AuthFailed()
        val raw = execute(Request.Builder().url("${session.baseUrl}/System/Info/Public").build())
        val info = JellyfinJson.parseServer(raw)
        SourceInfo(
            displayName = info.name.ifBlank { config.name },
            detail = "Jellyfin ${info.version}".trim(),
        )
    }

    /** path 为空列媒体库（Views），否则列该节点下的专辑 / 目录 / 歌曲。 */
    suspend fun browse(config: SourceConfig, path: String?): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val session = sessions[config.id] ?: throw SourceError.AuthFailed()
        val parent = path?.trim('/').orEmpty()
        val userId = Uri.encode(session.userId)
        val url = if (parent.isBlank()) {
            "${session.baseUrl}/Users/$userId/Views"
        } else {
            "${session.baseUrl}/Users/$userId/Items" +
                "?ParentId=${Uri.encode(parent)}" +
                "&IncludeItemTypes=Audio,MusicAlbum,MusicArtist,Folder,CollectionFolder,Playlist" +
                "&Recursive=false&Fields=MediaSources,Path,ImageTags,Artists,AlbumArtist,Album" +
                "&SortBy=SortName&SortOrder=Ascending&Limit=200"
        }
        val raw = execute(Request.Builder().url(url).build())
        val collator = Collator.getInstance(Locale.CHINA)
        JellyfinJson.parseItems(raw)
            .map { toEntry(config, session, it) }
            .sortedWith(
                compareByDescending<RemoteEntry> { it.isDirectory }
                    .thenComparator { a, b -> collator.compare(a.name, b.name) },
            )
            .take(200)
    }

    /** 原文件直传，Jellyfin 不会转码；认证头由 AuthInterceptor 注入。 */
    fun playbackUrl(config: SourceConfig, itemId: String): String =
        baseUrl(config) + JellyfinJson.playbackPath(Uri.encode(itemId))

    fun baseUrl(config: SourceConfig): String {
        val explicit = config.url?.trim()?.trimEnd('/')
        if (!explicit.isNullOrBlank() && explicit.startsWith("http", ignoreCase = true)) return explicit
        val host = config.host?.trim().orEmpty()
        if (host.isBlank()) return ""
        val port = config.port ?: DEFAULT_PORT
        val scheme = if (config.useTls || port == TLS_PORT) "https" else "http"
        return "$scheme://$host:$port"
    }

    fun close(configId: String) {
        val session = sessions.remove(configId) ?: return
        auth.clear(session.host, session.port)
    }

    private fun registerHeaders(session: Session) {
        auth.registerHeaders(
            session.host,
            session.port,
            mapOf(
                "Authorization" to authHeader(session.token),
                "X-Emby-Token" to session.token,
            ),
        )
    }

    private fun login(base: String, username: String, password: String, fingerprint: Int): Session {
        val uri = Uri.parse(base)
        val host = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
        // 登录端点不能带旧 token，否则新版服务端会把它当作已认证请求处理。
        auth.clear(host, port)
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }.toString()
        val request = Request.Builder()
            .url("$base/Users/AuthenticateByName")
            .header("Authorization", CLIENT_HEADER)
            .header("X-Emby-Authorization", CLIENT_HEADER)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val raw = execute(request)
        val parsed = JellyfinJson.parseAuth(raw) ?: throw SourceError.Protocol("AUTH_NO_TOKEN")
        return Session(
            baseUrl = base,
            host = host,
            port = port,
            userId = parsed.userId,
            token = parsed.token,
            fingerprint = fingerprint,
        )
    }

    private fun execute(request: Request): String {
        val response = runCatching { client.newCall(request).execute() }.getOrElse {
            throw SourceError.NetworkUnreachable(request.url.host)
        }
        return response.use { res ->
            when (res.code) {
                401, 403 -> throw SourceError.AuthFailed()
                404, 410 -> throw SourceError.PathNotFound(request.url.encodedPath)
                !in 200..299 -> throw SourceError.Protocol("HTTP_${res.code}")
            }
            val raw = res.body?.string().orEmpty()
            if (raw.isBlank()) throw SourceError.Protocol("EMPTY_RESPONSE")
            val head = raw.trimStart().firstOrNull()
            // 填错端口时经常撞上 NAS 网页首页或路由管理页，明确告诉用户不是 Jellyfin。
            if (head != '{' && head != '[') throw SourceError.Protocol("NOT_JELLYFIN")
            raw
        }
    }

    private fun toEntry(
        config: SourceConfig,
        session: Session,
        item: JellyfinJson.Item,
    ): RemoteEntry {
        val ext = item.container?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
            ?: item.path?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        val mime = MimeTypes.fromContainer(ext) ?: MimeTypes.fromPath(item.path.orEmpty())
        val name = when {
            item.isFolder -> item.name
            ext.isNullOrBlank() -> item.name
            item.name.endsWith(".$ext", ignoreCase = true) -> item.name
            else -> "${item.name}.$ext"
        }
        return RemoteEntry(
            name = name,
            path = item.id,
            isDirectory = item.isFolder,
            sizeBytes = item.sizeBytes,
            mimeType = if (item.isFolder) null else mime,
            playableUri = if (item.isFolder) null else playbackUrl(config, item.id),
            artist = item.artist,
            album = item.album,
            durationMs = item.durationMs,
            sampleRateHz = item.stream?.sampleRateHz,
            bitDepth = item.stream?.bitDepth,
            channels = item.stream?.channels,
            bitrateKbps = item.stream?.bitrateKbps,
            artworkUri = if (item.hasArtwork) {
                "${session.baseUrl}/Items/${Uri.encode(item.id)}/Images/Primary"
            } else null,
        )
    }

    private companion object {
        const val DEFAULT_PORT = 8096
        const val TLS_PORT = 8920
        val CLIENT_HEADER =
            "MediaBrowser Client=\"LiteListen\", Device=\"Android\", " +
                "DeviceId=\"litelisten-android\", Version=\"${BuildConfig.VERSION_NAME}\""
        fun authHeader(token: String): String = "$CLIENT_HEADER, Token=\"$token\""
    }
}
