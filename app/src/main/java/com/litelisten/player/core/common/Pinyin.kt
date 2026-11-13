package com.litelisten.player.core.common

import com.github.promeg.pinyinhelper.Pinyin as TinyPinyin
import java.util.Locale

object Pinyin {
    private val whitespace = Regex("\\s+")
    private val ftsSpecial = Regex("[\"*()\\-:^]")
    private const val MAX_NORM = 200

    /** 输出 原名|全拼|首字母，三个变体同列，LIKE 子串即可命中。 */
    fun normalize(input: String): String {
        val raw = input.trim().lowercase(Locale.ROOT)
        if (raw.isEmpty()) return ""
        val full = runCatching { TinyPinyin.toPinyin(raw, "").lowercase(Locale.ROOT) }
            .getOrDefault(raw).replace(whitespace, "")
        val initials = buildString {
            raw.forEach { ch ->
                if (ch.isWhitespace()) return@forEach
                val py = runCatching { TinyPinyin.toPinyin(ch) }.getOrDefault(ch.toString())
                append(py.firstOrNull() ?: ch)
            }
        }.lowercase(Locale.ROOT)
        val compact = raw.replace(whitespace, "")
        return listOf(compact, full, initials).distinct().joinToString("|").take(MAX_NORM)
    }

    fun normalizeLower(input: String): String = input.lowercase(Locale.ROOT).replace(whitespace, "")

    /** FTS4 MATCH 串：先按空白分词再加引号与前缀通配符，防止特殊字符注入。 */
    fun buildFtsQuery(input: String): String {
        val tokens = input.lowercase(Locale.ROOT)
            .replace(ftsSpecial, " ")
            .split(whitespace)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" OR ") { "\"$it\"*" }
    }

    /** 空格分词后每个词都能命中（AND 语义由调用方逐词过滤实现）。 */
    fun searchTokens(input: String): List<String> {
        val base = input.trim().lowercase(Locale.ROOT)
        if (base.isEmpty()) return emptyList()
        val full = runCatching { TinyPinyin.toPinyin(base, "").lowercase(Locale.ROOT) }.getOrDefault(base)
        return listOf(base, full).map { it.replace(whitespace, "") }.filter { it.isNotBlank() }.distinct()
    }
}
