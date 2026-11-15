package com.litelisten.player.core.player

/** 逐字时间轴：startMs 为绝对时间，endMs 为下一个字的开始时间（或行尾）。 */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    /** 增强 LRC 的逐字时间轴；普通 LRC 为空列表。 */
    val words: List<LyricWord> = emptyList(),
)

/**
 * 纯函数：支持多时间标签、offset、元数据行与增强 LRC（&lt;mm:ss.xx&gt; 逐字标签）；
 * 非法行直接跳过。
 */
object LrcParser {
    private val tagRegex = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val wordTagRegex = Regex("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>")
    private val offsetRegex = Regex("\\[offset:\\s*([+-]?\\d+)\\s*]", RegexOption.IGNORE_CASE)
    private val metaRegex = Regex("\\[(ti|ar|al|by|re|ve):.*?]", RegexOption.IGNORE_CASE)

    fun parse(content: String): List<LyricLine> {
        val offset = offsetRegex.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = ArrayList<LyricLine>()
        content.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val matches = tagRegex.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val body = tagRegex.replace(line, "").let { metaRegex.replace(it, "") }.trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fractionRaw = match.groupValues[3]
                val fraction = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLongOrNull()?.times(100) ?: 0L
                    2 -> fractionRaw.toLongOrNull()?.times(10) ?: 0L
                    else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                }
                val time = minutes * 60_000L + seconds * 1000L + fraction - offset
                lines.add(buildLine(time.coerceAtLeast(0L), body))
            }
        }
        return lines.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }
    }

    private fun buildLine(timeMs: Long, body: String): LyricLine {
        val wordMatches = wordTagRegex.findAll(body).toList()
        if (wordMatches.isEmpty()) return LyricLine(timeMs, body)
        val display = wordTagRegex.replace(body, "")
        val words = ArrayList<LyricWord>()
        wordMatches.forEachIndexed { index, match ->
            val start = parseWordTime(match) ?: return@forEachIndexed
            val startIdx = match.range.last + 1
            val endIdx = if (index + 1 < wordMatches.size) {
                wordMatches[index + 1].range.first
            } else {
                body.length
            }
            val text = body.substring(startIdx, endIdx).trim()
            if (text.isEmpty()) return@forEachIndexed
            words.add(LyricWord(start, 0L, text))
        }
        words.forEachIndexed { index, word ->
            val end = if (index + 1 < words.size) words[index + 1].startMs else word.startMs
            words[index] = word.copy(endMs = end)
        }
        return LyricLine(timeMs, display, words)
    }

    private fun parseWordTime(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fractionRaw = match.groupValues[3]
        val fraction = when (fractionRaw.length) {
            0 -> 0L
            1 -> fractionRaw.toLongOrNull()?.times(100) ?: 0L
            2 -> fractionRaw.toLongOrNull()?.times(10) ?: 0L
            else -> fractionRaw.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1000L + fraction
    }
}
