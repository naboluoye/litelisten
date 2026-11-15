package com.litelisten.player.core.player

/** 歌词文件与曲目的命名匹配规则；抽成纯函数便于单测。 */
object LyricsNaming {
    /**
     * 由歌曲路径推出同目录同名的 .lrc 路径。
     * 例：/Music/周杰伦/晴天.flac → /Music/周杰伦/晴天.lrc
     */
    fun sideCarPath(path: String): String? {
        // 以 / 结尾说明是目录而不是文件，推不出同名歌词。
        if (path.isEmpty() || path.endsWith("/")) return null
        val trimmed = path.trimEnd('/')
        if (trimmed.isEmpty()) return null
        val slash = trimmed.lastIndexOf('/')
        val name = if (slash >= 0) trimmed.substring(slash + 1) else trimmed
        if (name.isEmpty()) return null
        val base = name.substringBeforeLast('.', name)
        if (base.isEmpty()) return null
        val prefix = trimmed.substring(0, trimmed.length - name.length)
        return "$prefix$base.lrc"
    }

    /** 匹配用的归一化：去掉空白与全角空格、统一小写，中文不受影响。 */
    fun normalize(raw: String): String = raw.lowercase().replace(WHITESPACE, "").trim()

    private val WHITESPACE = Regex("[\\s\\u3000]+")
}
