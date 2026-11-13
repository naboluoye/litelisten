package com.litelisten.player.core.common

import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 「不联网」的代码级保证：只允许 RFC1918 / loopback / link-local / ULA / .local。
 * isPrivateHost 是纯字符串判断；短主机名另走 isPrivateOrResolvableToPrivate，
 * 解析后仍必须落在私网段才放行，公网出口一律拒绝。
 */
object LanAddress {
    private val verifiedHosts = ConcurrentHashMap.newKeySet<String>()
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

    /**
     * 局域网里 NAS / 路由器常填短主机名（nas、DISKSTATION）而不是 IP。
     * 这类名字不含点，只能由本地 DNS / NetBIOS 解析；先解析成 IP 再逐个校验，
     * 只要有一个地址落在公网就整体拒绝，保证「非私网地址一律拦截」不被绕过。
     * 解析失败同样拒绝（此时连接本来也不可能建立）。成功结果按进程缓存，避免每条请求都查 DNS。
     */
    fun isPrivateOrResolvableToPrivate(host: String): Boolean {
        if (isPrivateHost(host)) return true
        val h = host.trim().trim('[', ']').lowercase(Locale.ROOT)
        if (h.isEmpty() || h.length > 63 || h.contains('.') || h.contains(':') ||
            h.any { !it.isLetterOrDigit() && it != '-' }
        ) return false
        if (verifiedHosts.contains(h)) return true
        val addresses = runCatching { InetAddress.getAllByName(h) }.getOrNull()
        val ok = !addresses.isNullOrEmpty() && addresses.all { addr ->
            isPrivateHost(addr.hostAddress.orEmpty().substringBefore('%'))
        }
        if (ok) verifiedHosts.add(h)
        return ok
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
