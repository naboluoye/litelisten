package com.litelisten.player.ui.navigation

object Routes {
    const val LIBRARY = "library"
    const val SOURCES = "sources"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now_playing"

    const val ALBUM = "album/{key}"
    const val ARTIST = "artist/{key}"
    const val BROWSER = "browser/{sourceId}?path={path}"
    const val ADD_SMB = "add_smb"
    const val ADD_WEBDAV = "add_webdav"
    const val QUEUE = "queue"
    const val LYRICS = "lyrics"
    const val EQUALIZER = "equalizer"

    fun album(key: String) = "album/$key"
    fun artist(key: String) = "artist/$key"
    fun browser(sourceId: String, path: String = "") = "browser/$sourceId?path=$path"

    val TOP_LEVEL = setOf(LIBRARY, SOURCES, SEARCH, SETTINGS)
}
