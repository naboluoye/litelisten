package com.litelisten.player.core.player

data class LyricLine(val timeMs: Long, val text: String)

/** 纯函数：支持多时间标签、offset、元数据行；非法行直接跳过。 */
object LrcParser {
    private val tagRegex = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
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
            val text = tagRegex.replace(line, "").let { metaRegex.replace(it, "") }.trim()
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
                lines.add(LyricLine(time.coerceAtLeast(0L), text))
            }
        }
        return lines.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }
    }
}
