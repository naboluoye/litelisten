package com.litelisten.player.core.source.dlna

import android.util.Xml
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
import com.litelisten.player.core.network.LocalNetworkHolder
import com.litelisten.player.core.source.api.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** DLNA/UPnP ContentDirectory Browse；DIDL-Lite 里的资源 URL 直接交给播放器。 */
@Singleton
class DlnaSourceProvider @Inject constructor(
    private val base: OkHttpClient,
    private val networkHolder: LocalNetworkHolder,
) : SourceProvider {
    /**
     * DLNA 的 SSDP 探测能找到设备，但控制请求（SOAP Browse）默认走系统「默认网络」。
     * 手机在「Wi-Fi 无互联网 + 开着蜂窝数据」时默认网络是蜂窝，192.168.x.x 的控制地址
     * 全部不可达，结果设备搜得到却连不上。改用局域网 Network 的 socketFactory 强制绑定。
     */
    private val client: OkHttpClient
        get() = networkHolder.current()?.socketFactory?.let { sf ->
            base.newBuilder().socketFactory(sf).build()
        } ?: base
    override val type: SourceType = SourceType.DLNA

    override suspend fun testConnection(config: SourceConfig): Result<SourceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            browseXml(config.controlUrl.orEmpty(), "0", 0, 1)
            SourceInfo(config.name, "DLNA")
        }
    }

    override suspend fun listShares(config: SourceConfig): Result<List<ShareInfo>> =
        Result.success(emptyList())

    override suspend fun browse(config: SourceConfig, path: String?): Result<List<RemoteEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val didl = browseXml(config.controlUrl.orEmpty(), path?.takeIf { it != "/" } ?: "0", 0, 200)
                parseDidl(didl)
            }
        }

    override suspend fun search(config: SourceConfig, query: String, limit: Int): Result<List<Track>> =
        Result.failure(SourceError.Unsupported("DLNA_SEARCH"))

    override suspend fun resolvePlayable(config: SourceConfig, track: Track): Result<PlayableMedia> = runCatching {
        PlayableMedia(
            uri = track.uri,
            headers = mapOf(
                "getcontentFeatures.dlna.org" to "1",
                "transferMode.dlna.org" to "Streaming",
            ),
        )
    }

    override fun dataSourceFactory(config: SourceConfig): DataSource.Factory? = null

    override suspend fun close(configId: String) = Unit

    private fun browseXml(controlUrl: String, objectId: String, start: Int, count: Int): String {
        if (controlUrl.isBlank()) throw SourceError.Protocol("NO_CONTROL_URL")
        val envelope = "<?xml version=\"1.0\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:Browse xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\">" +
            "<ObjectID>$objectId</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag>" +
            "<Filter>*</Filter><StartingIndex>$start</StartingIndex>" +
            "<RequestedCount>$count</RequestedCount><SortCriteria>+dc:title</SortCriteria>" +
            "</u:Browse></s:Body></s:Envelope>"
        val request = Request.Builder()
            .url(controlUrl)
            .header("SOAPACTION", "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\"")
            .header("User-Agent", "LiteListen/1.0 UPnP/1.1 DLNADOC/1.50")
            .header("Accept", "*/*")
            .header("Connection", "close")
            .post(envelope.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()
        val response = runCatching { client.newCall(request).execute() }.getOrElse {
            throw SourceError.NetworkUnreachable(controlUrl)
        }
        return response.use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw SourceError.Protocol("SOAP_${res.code}")
            if (raw.isBlank() || !raw.trimStart().startsWith("<")) {
                throw SourceError.Protocol("NO_DIDL_RESULT")
            }
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(raw))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG &&
                    parser.name?.substringAfterLast(':')?.equals("Result", true) == true
                ) {
                    return@use parser.nextText()
                }
                event = parser.next()
            }
            // 空目录的设备只回 BrowseResponse 不带 Result，按空目录处理而不是报协议错。
            if (raw.contains("BrowseResponse", true)) return@use ""
            throw SourceError.Protocol("NO_DIDL_RESULT")
        }
    }

    private fun parseDidl(didl: String): List<RemoteEntry> {
        if (didl.isBlank()) return emptyList()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(didl))
        val result = ArrayList<RemoteEntry>()
        var id = ""
        var title = ""
        var resource = ""
        var directory = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':')?.lowercase(Locale.ROOT).orEmpty()
            if (event == XmlPullParser.START_TAG) {
                when (tag) {
                    "container" -> {
                        directory = true
                        id = parser.getAttributeValue(null, "id").orEmpty()
                        title = ""
                        resource = ""
                    }
                    "item" -> {
                        directory = false
                        id = parser.getAttributeValue(null, "id").orEmpty()
                        title = ""
                        resource = ""
                    }
                    "title" -> title = parser.nextText()
                    "res" -> {
                        val candidate = parser.nextText()
                        if (resource.isBlank()) resource = candidate
                    }
                }
            } else if (event == XmlPullParser.END_TAG && (tag == "item" || tag == "container")) {
                if (id.isNotBlank()) {
                    val playable = directory || resource.isNotBlank()
                    if (playable) {
                        result.add(
                            RemoteEntry(
                                name = title.ifBlank { id.substringAfterLast('$', id) },
                                path = id,
                                isDirectory = directory,
                                mimeType = if (directory) null else MimeTypes.fromPath(resource.substringBefore('?')),
                                playableUri = resource.takeIf { it.isNotBlank() },
                            ),
                        )
                    }
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
}
