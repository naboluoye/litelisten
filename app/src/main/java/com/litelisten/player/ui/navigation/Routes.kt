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
    const val ADD_SMB = "add_smb"
    const val ADD_WEBDAV = "add_webdav"
    const val QUEUE = "queue"
    const val LYRICS = "lyrics"
    const val EQUALIZER = "equalizer"

    fun album(name: String) = "album/${Uri.encode(name)}"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun folder(name: String) = "folder/${Uri.encode(name)}"
    fun browser(sourceId: String, path: String = "") =
        "browser/${Uri.encode(sourceId)}?path=${Uri.encode(path)}"

    val TOP_LEVEL = setOf(LIBRARY, SOURCES, SEARCH, SETTINGS)
}
