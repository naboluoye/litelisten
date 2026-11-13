package com.litelisten.player.core.common

import java.util.Locale

/**
 * 「不联网」的代码级保证：只允许 RFC1918 / loopback / link-local / ULA / .local。
 * 纯字符串判断、不做 DNS 解析，避免校验本身触发外网请求；主机名须用 IP 或 .local。
 */
object LanAddress {
    fun isPrivateHost(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase(Locale.ROOT)
        if (h.isEmpty()) return false
        if (h == "localhost" || h == "::" || h.endsWith(".local")) return true
        if (h.startsWith("::ffff:")) return isPrivateHost(h.removePrefix("::ffff:"))

        parseIpv4(h)?.let { octets ->
            return when {
                octets[0] == 10 -> true
                octets[0] == 127 -> true
                octets[0] == 192 && octets[1] == 168 -> true
                octets[0] == 172 && octets[1] in 16..31 -> true
                octets[0] == 169 && octets[1] == 254 -> true
                else -> false
            }
        }
        // IPv6：loopback / link-local fe80::/10 / ULA fc00::/7
        return h == "::1" ||
            h == "0:0:0:0:0:0:0:1" ||
            h.startsWith("fe80:") ||
            h.startsWith("fe8") ||
            h.startsWith("fe9") ||
            h.startsWith("fea") ||
            h.startsWith("feb") ||
            h.startsWith("fc") ||
            h.startsWith("fd")
    }

    private fun parseIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val result = IntArray(4)
        for ((index, part) in parts.withIndex()) {
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            result[index] = value
        }
        return result
    }
}
