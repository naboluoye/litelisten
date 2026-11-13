package com.litelisten.player.ui.navigation

import android.net.Uri

object Routes {
    const val LIBRARY = "library"
    const val SOURCES = "sources"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now_playing"

    const val ALBUM = "album/{key}"
    const val ARTIST = "artist/{key}"
    const val FOLDER = "folder/{key}"
    const val BROWSER = "browser/{sourceId}?path={path}"
    const val ADD_SMB = "add_smb?host={host}&port={port}"
    const val ADD_WEBDAV = "add_webdav?url={url}"
    const val QUEUE = "queue"
    const val LYRICS = "lyrics"
    const val EQUALIZER = "equalizer"

    fun album(name: String) = "album/${Uri.encode(name)}"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun folder(name: String) = "folder/${Uri.encode(name)}"
    fun browser(sourceId: String, path: String = "") =
        "browser/${Uri.encode(sourceId)}?path=${Uri.encode(path)}"

    /** 从「发现的设备」点进来时预填 IP / 地址，用户只需补共享名和账号。 */
    fun addSmb(host: String = "", port: Int = 445) =
        "add_smb?host=${Uri.encode(host)}&port=$port"

    fun addWebdav(host: String = "", port: Int = 80) =
        "add_webdav?url=" + Uri.encode(
            if (host.isBlank()) "" else "http://$host:$port/",
        )

    val TOP_LEVEL = setOf(LIBRARY, SOURCES, SEARCH, SETTINGS)
}
