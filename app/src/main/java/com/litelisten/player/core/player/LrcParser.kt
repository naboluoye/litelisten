package com.litelisten.player.core.player

/** 逐字时间轴：startMs 为绝对时间，endMs 为下一个字的开始时间（或行尾）。 */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    /** 增强 LRC 的逐字时间轴；普通 LRC 为空列表。 */
    val words: List<LyricWord> = emptyList(),
    /** 行结束时间（下一行开始时间）；最后一行为 0，表示未知。 */
    val endMs: Long = 0L,
) {
    /** 跟唱循环与染色用的行尾；未知时给一个保守估计，避免最后一行永远唱不完。 */
    val effectiveEndMs: Long get() = if (endMs > timeMs) endMs else timeMs + DEFAULT_LINE_MS

    companion object {
        const val DEFAULT_LINE_MS = 2_500L
    }
}

/**
 * 纯函数：支持多时间标签、offset、元数据行与增强 LRC（&lt;mm:ss.xx&gt; 逐字标签）；
 * 非法行直接跳过。解析完成后统一回填行尾时间与逐字时长，供卡拉 OK 式染色与单句循环使用。
 */
object LrcParser {
    // 小数位放宽到 1~6 位：少数编码器会输出 [00:12.3456]，统一取前 3 位换算成毫秒。
    private val tagRegex = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,6}))?]")
    private val wordTagRegex = Regex("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,6}))?>")
    private val offsetRegex = Regex("\\[offset:\\s*([+-]?\\d+)\\s*]", RegexOption.IGNORE_CASE)
    private val metaRegex = Regex("\\[(ti|ar|al|by|re|ve):.*?]", RegexOption.IGNORE_CASE)

    private const val ESTIMATED_WORD_MS = 220L

    /** 小数部分换算毫秒：1 位=百位，2 位=十位，>=3 位取前三位。 */
    private fun fractionMs(raw: String): Long = when {
        raw.isEmpty() -> 0L
        raw.length == 1 -> raw.toLongOrNull()?.times(100) ?: 0L
        raw.length == 2 -> raw.toLongOrNull()?.times(10) ?: 0L
        else -> raw.take(3).toLongOrNull() ?: 0L
    }

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
                val fraction = fractionMs(match.groupValues[3])
                val time = (minutes * 60_000L + seconds * 1000L + fraction - offset).coerceAtLeast(0L)
                lines.add(buildLine(time, body, offset))
            }
        }
        val ordered = lines.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }
        return closeTimeline(ordered)
    }

    /** 回填行尾：下一行开始即本行结束；最后一行为 0（由 effectiveEndMs 兜底）。 */
    private fun closeTimeline(lines: List<LyricLine>): List<LyricLine> =
        lines.mapIndexed { index, line ->
            val nextStart = lines.getOrNull(index + 1)?.timeMs
            val end = if (nextStart != null && nextStart > line.timeMs) nextStart else 0L
            if (line.words.isEmpty()) {
                line.copy(endMs = end)
            } else {
                line.copy(endMs = end, words = closeWords(line.words, end))
            }
        }

    /** 逐字收尾：优先用下一个字，其次用行尾，都没有时给一个短估计，避免零时长。 */
    private fun closeWords(words: List<LyricWord>, lineEnd: Long): List<LyricWord> {
        val result = words.toMutableList()
        for (index in result.indices) {
            val current = result[index]
            val nextStart = result.getOrNull(index + 1)?.startMs
            val end = when {
                nextStart != null && nextStart > current.startMs -> nextStart
                lineEnd > current.startMs -> lineEnd
                else -> current.startMs + ESTIMATED_WORD_MS
            }
            result[index] = current.copy(endMs = end)
        }
        return result
    }

    private fun buildLine(timeMs: Long, body: String, offset: Long): LyricLine {
        val wordMatches = wordTagRegex.findAll(body).toList()
        if (wordMatches.isEmpty()) return LyricLine(timeMs, body)
        val display = wordTagRegex.replace(body, "")
        val words = ArrayList<LyricWord>()
        wordMatches.forEachIndexed { index, match ->
            // 逐字标签同样是绝对时间，必须一起减 offset，否则带 offset 的歌词会整体错位。
            val raw = parseWordTime(match) ?: return@forEachIndexed
            val start = (raw - offset).coerceAtLeast(0L)
            val startIdx = match.range.last + 1
            val endIdx = if (index + 1 < wordMatches.size) {
                wordMatches[index + 1].range.first
            } else {
                body.length
            }
            val text = body.substring(startIdx, endIdx).trim()
            if (text.isEmpty()) return@forEachIndexed
            words.add(LyricWord(start, start, text))
        }
        return LyricLine(timeMs, display, words)
    }

    private fun parseWordTime(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        return minutes * 60_000L + seconds * 1000L + fractionMs(match.groupValues[3])
    }
}
