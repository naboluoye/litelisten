package com.litelisten.player.core.common

import java.util.Locale

object Pinyin {
    private val whitespace = Regex("\\s+")
    private val ftsSpecial = Regex("[\"*()\\-:^]")

    fun normalizeLower(input: String): String =
        input.lowercase(Locale.ROOT).replace(whitespace, "")

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

    // P1/M6：接入 TinyPinyin 后在此追加全拼与首字母 token，例如 晴天 -> qingtian / qt。
}
