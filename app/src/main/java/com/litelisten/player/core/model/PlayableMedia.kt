package com.litelisten.player.core.model

/** 播放所需的一切：URI + 请求头（WebDAV / DLNA 需要）。 */
data class PlayableMedia(
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val isSeekable: Boolean = true,
)
