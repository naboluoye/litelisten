package com.litelisten.player.core.source.webdav

import android.net.Uri
import android.util.Xml
import com.litelisten.player.core.common.MimeTypes
import com.litelisten.player.core.model.RemoteEntry
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceError
import com.litelisten.player.core.network.AuthInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** WebDAV PROPFIND depth=1；认证头由 AuthInterceptor 按 host 注入，不进 URI。 */
@Singleton
class WebDavClient @Inject constructor(
    private val client: OkHttpClient,
    private val auth: AuthInterceptor,
) {
    fun prepare(config: SourceConfig, username: String?, password: String?) {
        val url = runCatching { Uri.parse(config.url.orEmpty()) }.getOrNull() ?: return
        val host = url.host ?: return
        val port = if (url.port > 0) url.port else if (url.scheme == "https") 443 else 80
        auth.register(host, port, username, password)
    }

    fun urlOf(config: SourceConfig, path: String?): String {
        val base = config.url.orEmpty().trimEnd('/')
        val relative = path.orEmpty().trim('/')
        if (relative.isEmpty()) return "$base/"
        val encoded = relative.split('/').joinToString("/") { Uri.encode(it) }
        return "$base/$encoded"
    }

    suspend fun propfind(config: SourceConfig, path: String?, depth: Int): List<RemoteEntry> {
        val url = urlOf(config, path)
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .header("Depth", depth.toString())
            .build()
        val response = runCatching { client.newCall(request).execute() }.getOrElse {
            throw SourceError.NetworkUnreachable(config.host ?: Uri.parse(url).host.orEmpty())
        }
        response.use { res ->
            when (res.code) {
                401, 403 -> throw SourceError.AuthFailed()
                404, 410 -> throw SourceError.PathNotFound(path.orEmpty())
                !in 200..299 -> if (res.code == 207) Unit else throw SourceError.Protocol("HTTP_${res.code}")
            }
            val xml = res.body?.string().orEmpty()
            if (xml.isBlank()) throw SourceError.Protocol("EMPTY_RESPONSE")
            return parse(xml, config, path)
        }
    }

    /**
     * href 一律相对 config.url 的 path 计算，而不是本次 PROPFIND 请求 URL 的 path。
     * 否则进入二级目录后去掉的是当前目录，entry.path 会丢掉父层级，
     * 播放与整目录导入时拼出来的 URI 就会 404。与 SMB 的累积路径坐标系保持一致。
     */
    private fun parse(xml: String, config: SourceConfig, requestedPath: String?): List<RemoteEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        val basePath = Uri.parse(config.url.orEmpty()).path.orEmpty().trimEnd('/')
        val selfPath = requestedPath?.trim('/').orEmpty()
        val result = ArrayList<RemoteEntry>()
        var current: MutableResponse? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':')?.lowercase(Locale.ROOT).orEmpty()
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "response" -> current = MutableResponse()
                        // <D:collection/> 是自闭合标签：必须在 START_TAG 就置位，
                        // 等到 </D:resourcetype> 再看会被 END_TAG 复位，导致文件夹被当成文件。
                        "collection" -> current?.directory = true
                        "href", "getcontentlength", "getcontenttype", "getlastmodified" ->
                            current?.pending = tag
                    }
                }
                XmlPullParser.TEXT -> current?.let { item ->
                    when (item.pending) {
                        // Uri.decode 只解 %XX；URLDecoder 会把文件名里的 '+' 变成空格。
                        "href" -> item.href = parser.text
                        "getcontentlength" -> item.size = parser.text?.toLongOrNull()
                        "getcontenttype" -> item.mime = parser.text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "response" -> current?.let { item ->
                            val href = item.href.orEmpty()
                            val decoded = Uri.decode(href)
                            val rawPath = if (decoded.startsWith("http", true)) {
                                Uri.parse(decoded).path.orEmpty()
                            } else decoded
                            val relative = if (rawPath == basePath || rawPath == "$basePath/") {
                                ""
                            } else {
                                rawPath.removePrefix("$basePath/").trim('/')
                            }
                            if (relative.isNotBlank() && relative != selfPath && relative != "$selfPath/") {
                                val name = relative.substringAfterLast('/')
                                result.add(
                                    RemoteEntry(
                                        name = name,
                                        path = "/$relative",
                                        isDirectory = item.directory,
                                        sizeBytes = item.size,
                                        mimeType = item.mime?.takeIf { it.isNotBlank() }
                                            ?: if (item.directory) null else MimeTypes.fromPath(name),
                                    ),
                                )
                            }
                        }
                    }
                    current?.pending = null
                }
            }
            event = parser.next()
        }
        val collator = java.text.Collator.getInstance(Locale.CHINA)
        return result.sortedWith(
            compareByDescending<RemoteEntry> { it.isDirectory }
                .thenComparator { a, b -> collator.compare(a.name, b.name) },
        ).take(200)
    }

    private data class MutableResponse(
        var href: String? = null,
        var size: Long? = null,
        var mime: String? = null,
        var directory: Boolean = false,
        var pending: String? = null,
    )

    private companion object {
        const val PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<D:propfind xmlns:D=\"DAV:\"><D:prop>" +
                "<D:displayname/><D:getcontentlength/><D:getlastmodified/>" +
                "<D:getcontenttype/><D:resourcetype/>" +
                "</D:prop></D:propfind>"
    }
}
