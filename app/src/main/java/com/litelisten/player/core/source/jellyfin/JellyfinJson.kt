package com.litelisten.player.core.source.jellyfin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Jellyfin REST 响应的纯解析层：不碰 Android API，可直接跑 JVM 单元测试。
 * Jellyfin 各版本字段差异较大，一律按可空字段取值，缺失时由上层兜底。
 */
object JellyfinJson {
    data class Auth(val token: String, val userId: String)

    data class Server(val name: String, val version: String)

    data class Stream(
        val sampleRateHz: Int? = null,
        val bitDepth: Int? = null,
        val channels: Int? = null,
        val bitrateKbps: Int? = null,
    )

    data class Item(
        val id: String,
        val name: String,
        val type: String,
        val isFolder: Boolean,
        val container: String?,
        val path: String?,
        val sizeBytes: Long?,
        val durationMs: Long?,
        val artist: String?,
        val album: String?,
        val hasArtwork: Boolean,
        val stream: Stream?,
    )

    /**
     * static=true 让服务器直接回传原始文件，不转码、不降码率，是无损播放的关键。
     * 不用 /Items/{id}/Download：那个端点受账号「允许下载」权限约束，
     * 普通用户常被 403 挡住，而静态流属于播放权限，默认就有。
     */
    fun playbackPath(itemId: String): String = "/Audio/$itemId/stream?static=true"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val folderTypes = setOf(
        "musicalbum", "musicartist", "folder", "collectionfolder",
        "playlist", "album", "artist", "userview", "musicgenre", "genre",
    )

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
    private fun JsonObject.str(key: String): String? = primitive(key)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull
    private fun JsonObject.long(key: String): Long? = primitive(key)?.longOrNull
    private fun JsonObject.bool(key: String): Boolean? = primitive(key)?.booleanOrNull

    private fun root(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

    fun parseAuth(raw: String): Auth? {
        val obj = root(raw) ?: return null
        val token = obj.str("AccessToken")?.takeIf { it.isNotBlank() } ?: return null
        val userId = (obj["User"] as? JsonObject)?.str("Id")?.takeIf { it.isNotBlank() }
            ?: obj.str("UserId")?.takeIf { it.isNotBlank() }
            ?: return null
        return Auth(token, userId)
    }

    fun parseServer(raw: String): Server {
        val obj = root(raw) ?: return Server("", "")
        return Server(
            name = obj.str("ServerName").orEmpty(),
            version = obj.str("Version").orEmpty(),
        )
    }

    fun parseItems(raw: String): List<Item> {
        val obj = root(raw) ?: return emptyList()
        val items = obj["Items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.str("Id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val type = item.str("Type").orEmpty()
            val source = (item["MediaSources"] as? JsonArray)?.firstOrNull() as? JsonObject
            val streams = source?.get("MediaStreams") as? JsonArray
            val audio = streams?.firstOrNull { element ->
                (element as? JsonObject)?.str("Type").equals("Audio", ignoreCase = true)
            } as? JsonObject
            val artists = item["Artists"] as? JsonArray
            val artist = artists?.firstOrNull()?.let { it as? JsonPrimitive }?.contentOrNull
                ?: item.str("AlbumArtist")
            val hasArtwork = (item["ImageTags"] as? JsonObject)?.str("Primary") != null
            Item(
                id = id,
                name = item.str("Name")?.takeIf { it.isNotBlank() } ?: id,
                type = type,
                isFolder = item.bool("IsFolder") ?: type.lowercase() in folderTypes,
                container = item.str("Container") ?: source?.str("Container"),
                path = item.str("Path") ?: source?.str("Path"),
                sizeBytes = source?.long("Size") ?: item.long("Size"),
                durationMs = item.long("RunTimeTicks")?.let { it / 10_000L },
                artist = artist,
                album = item.str("Album"),
                hasArtwork = hasArtwork,
                stream = audio?.let {
                    Stream(
                        sampleRateHz = it.int("SampleRate"),
                        bitDepth = it.int("BitDepth"),
                        channels = it.int("Channels"),
                        bitrateKbps = it.long("BitRate")?.let { bps -> (bps / 1000L).toInt() },
                    )
                },
            )
        }
    }
}
