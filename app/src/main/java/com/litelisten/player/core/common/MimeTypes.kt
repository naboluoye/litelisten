package com.litelisten.player.core.common

import java.util.Locale

/**
 * 支持矩阵见《02 PRD》：APE / DSD / WMA 明确不支持，返回 null 让 UI 灰显。
 * HiFi 范围的 FLAC / WAV / AIFF / ALAC 都是原始文件直传，播放链路不做转码。
 */
object MimeTypes {
    private val supported = mapOf(
        "mp3" to "audio/mpeg",
        "flac" to "audio/flac",
        "m4a" to "audio/mp4",
        "alac" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "opus" to "audio/ogg",
        "aiff" to "audio/aiff",
        "aif" to "audio/aiff",
    )

    /** 无损容器的扩展名；用于音质标签，不决定是否可播放。 */
    private val lossless = setOf("flac", "wav", "aiff", "aif", "alac")

    private val labels = mapOf(
        "audio/flac" to "FLAC",
        "audio/wav" to "WAV",
        "audio/aiff" to "AIFF",
        "audio/mp4" to "ALAC/AAC",
        "audio/mpeg" to "MP3",
        "audio/aac" to "AAC",
        "audio/ogg" to "OGG",
    )

    fun fromExtension(extension: String): String? =
        supported[extension.trim().removePrefix(".").lowercase(Locale.ROOT)]

    fun fromPath(path: String): String? = fromExtension(path.substringAfterLast('.', ""))

    fun isSupported(path: String): Boolean = fromPath(path) != null

    /** 媒体服务器给的 Container 可能带点或大小写，统一走同一张表。 */
    fun fromContainer(container: String?): String? = container?.let { fromExtension(it) }

    /** 英文格式代号，作为音质行的前缀；不需要本地化。 */
    fun shortLabel(mimeType: String?): String? = mimeType?.let { labels[it.lowercase(Locale.ROOT)] }

    fun isLossless(pathOrMime: String?): Boolean {
        if (pathOrMime.isNullOrBlank()) return false
        val value = pathOrMime.lowercase(Locale.ROOT)
        val ext = value.substringAfterLast('.', value).removePrefix(".")
        return ext in lossless || isLosslessMime(value)
    }

    fun isLosslessMime(mimeType: String?): Boolean =
        mimeType != null && mimeType.lowercase(Locale.ROOT) in setOf(
            "audio/flac", "audio/wav", "audio/aiff",
        )

    /** 高于 CD 规格（16 bit / 44.1 kHz）即视为高解析。 */
    fun isHiRes(sampleRateHz: Int?, bitDepth: Int?): Boolean =
        (sampleRateHz ?: 0) > 48_000 || (bitDepth ?: 0) > 16
}
