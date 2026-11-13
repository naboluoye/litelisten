package com.litelisten.player.core.common

import java.util.Locale

/** 支持矩阵见《02 PRD》：APE / DSD / WMA 明确不支持，返回 null 让 UI 灰显。 */
object MimeTypes {
    private val supported = mapOf(
        "mp3" to "audio/mpeg",
        "flac" to "audio/flac",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "opus" to "audio/ogg",
        "aiff" to "audio/aiff",
        "aif" to "audio/aiff",
    )

    fun fromExtension(extension: String): String? =
        supported[extension.trim().removePrefix(".").lowercase(Locale.ROOT)]

    fun fromPath(path: String): String? = fromExtension(path.substringAfterLast('.', ""))

    fun isSupported(path: String): Boolean = fromPath(path) != null
}
