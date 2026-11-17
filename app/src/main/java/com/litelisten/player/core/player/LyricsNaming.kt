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

    /**
     * 一个名字的"匹配变体"集合：归一化原样 + 去序号 + 取最后一个分隔符后的部分。
     * 例："周杰伦 - 晴天" → {周杰伦-晴天, 晴天}；"01.晴天" → {01晴天, 晴天}。
     * 只生成变体、不做模糊包含，避免"爱"误配到"恋爱"。
     */
    fun variants(raw: String): Set<String> {
        // 统一去扩展名：调用方可能传「晴天.lrc」也可能传「晴天」。
        val n = normalize(baseName(raw))
        if (n.isEmpty()) return emptySet()
        val result = LinkedHashSet<String>()
        result.add(n)
        val noLeadingNo = stripLeadingTrackNo(n)
        if (noLeadingNo.isNotEmpty()) result.add(noLeadingNo)
        // "歌手 - 歌名" / "歌手_歌名"：取最后一段作为歌名变体。
        val lastPart: String? = SEPARATOR.split(noLeadingNo).lastOrNull()
        if (lastPart != null) {
            val trimmed = lastPart.trim(*PUNCTUATION)
            if (trimmed.isNotEmpty()) result.add(trimmed)
        }
        return result
    }

    /**
     * 判断歌词文件名（去扩展名）是否与任一歌曲名候选匹配：
     * 两边各自的变体集合有交集即算命中。
     */
    fun matches(lyricBase: String, songCandidates: Collection<String>): Boolean {
        val lyricVariants = variants(lyricBase)
        if (lyricVariants.isEmpty()) return false
        for (candidate in songCandidates) {
            if (variants(candidate).any { it in lyricVariants }) return true
        }
        return false
    }

    /**
     * 由歌曲的多个来源名推导出去掉扩展名后的候选集合（已归一化）。
     * 传入顺序：真实文件名、path 末段、title（越靠前越可信）。
     */
    fun songCandidates(vararg rawNames: String): List<String> =
        rawNames.mapNotNull { raw ->
            val n = normalize(baseName(raw))
            n.ifEmpty { null }
        }.distinct()

    /**
     * 去扩展名：只有最后一个点之后是「像扩展名」的短片段（无空白、1~5 字符）才截。
     * 避免把「01. 晴天」「周杰伦 . 晴天」这类歌名里的点误当成扩展名而截成「01」。
     */
    fun baseName(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return name
        val suffix = name.substring(dot + 1)
        val looksLikeExt = suffix.isNotEmpty() && suffix.length <= 5 &&
            suffix.none { it.isWhitespace() || it == '\u3000' }
        return if (looksLikeExt) name.substring(0, dot) else name
    }

    private fun stripLeadingTrackNo(normalized: String): String {
        val replaced: String = LEADING_TRACK_NO.replace(normalized, "")
        return replaced.trim(*PUNCTUATION)
    }

    private val WHITESPACE = Regex("[\\s\\u3000]+")
    // 序号后允许有分隔符（01. / 01- / 01_），也允许紧跟正文（[01]晴天 / 01晴天）。
    private val LEADING_TRACK_NO = Regex("^[\\[\\(]?\\d{1,3}[\\]\\)]([.\\-_\\s]*)?|^\\d{1,3}[.\\-_\\s]+")
    private val SEPARATOR = Regex("[-_–—]")
    private val PUNCTUATION = charArrayOf('-', '_', '–', '—', '.', ' ', '\u3000')
}
