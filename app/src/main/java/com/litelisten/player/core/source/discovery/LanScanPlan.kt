package com.litelisten.player.core.source.discovery

import com.litelisten.player.core.model.SourceType

/**
 * 局域网扫描的纯计算部分，不碰系统 API，便于单元测试。
 * 只在本机所在的子网内枚举地址；掩码比 /24 大时收窄到 /24，
 * 避免 /16 网段一次生成 6 万多个目标。
 */
object LanScanPlan {
    const val MAX_HOSTS = 254
    const val CONNECT_TIMEOUT_MS = 220
    const val NBNS_TIMEOUT_MS = 300
    const val DEFAULT_PREFIX = 24

    const val SMB_PORT = 445
    const val SMB_NETBIOS_PORT = 139
    const val JELLYFIN_PORT = 8096
    const val JELLYFIN_TLS_PORT = 8920

    /**
     * 按命中优先级排列：先找最像文件共享的端口，命中即停。
     * Jellyfin 端口必须排在 80/443 之前，否则同时开了网页服务的 Jellyfin 主机会
     * 被判成 WebDAV，用户就永远看不到「媒体服务器」这个入口。
     */
    val PROBE_PORTS = intArrayOf(
        SMB_PORT, SMB_NETBIOS_PORT, JELLYFIN_PORT, JELLYFIN_TLS_PORT,
        5000, 5001, 5005, 8080, 8081, 8800, 80, 443,
    )

    fun hosts(selfIp: Int, prefixLength: Int, limit: Int = MAX_HOSTS): List<String> {
        val bits = prefixLength.coerceIn(24, 30)
        val unsigned = selfIp.toLong() and 0xffffffffL
        val mask = (0xffffffffL shl (32 - bits)) and 0xffffffffL
        val network = unsigned and mask
        val size = 1L shl (32 - bits)
        val result = ArrayList<String>(minOf(limit, size.toInt()))
        var offset = 1L
        while (offset < size - 1 && result.size < limit) {
            val candidate = network + offset
            if (candidate != unsigned) result.add(intToIp(candidate))
            offset++
        }
        return result
    }

    fun intToIp(value: Long): String = buildString {
        append((value shr 24) and 0xff)
        append('.')
        append((value shr 16) and 0xff)
        append('.')
        append((value shr 8) and 0xff)
        append('.')
        append(value and 0xff)
    }

    fun ipToInt(host: String): Int? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            value = (value shl 8) or octet.toLong()
        }
        return value.toInt()
    }

    fun isJellyfinPort(port: Int): Boolean =
        port == JELLYFIN_PORT || port == JELLYFIN_TLS_PORT

    /** 445/139 是 SMB，8096/8920 是 Jellyfin，其余开放端口按 WebDAV 入口建议。 */
    fun typeOf(port: Int): SourceType = when {
        port == SMB_PORT || port == SMB_NETBIOS_PORT -> SourceType.SMB
        isJellyfinPort(port) -> SourceType.JELLYFIN
        else -> SourceType.WEBDAV
    }

    fun schemeOf(port: Int): String =
        if (port == JELLYFIN_TLS_PORT || port == 443) "https" else "http"
}
