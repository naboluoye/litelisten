package com.litelisten.player.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 保存当前真正连着局域网的那张 Network（Wi-Fi / 以太网）。
 *
 * DLNA 的 SSDP、SOAP 控制请求，以及 SMB/WebDAV 探测都默认走系统「默认网络」。
 * 手机在「Wi-Fi 无互联网 + 开着蜂窝数据」时，系统会把默认网络切到蜂窝，
 * 结果发给 192.168.x.x 的内网请求全部不可达，NAS / 媒体服务器一台都连不上。
 *
 * 发现阶段把真正的局域网 Network 记在这里（[set]），DLNA 播放器与设备描述请求再使用它的
 * socketFactory 强制绑定到这张网卡，绕开系统的默认路由。[current] 会在内存记录为空
 * （例如杀进程重启后）时即时解析一次，保证浏览 / 播放也始终绑对网络。
 */
@Singleton
class LocalNetworkHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile var network: Network? = null
        private set

    fun set(network: Network?) {
        this.network = network
    }

    /** 返回已记录的局域网 Network；为空（进程重启后）则即时解析一次并缓存。 */
    fun current(): Network? {
        val existing = network
        if (existing != null) return existing
        val resolved = resolve()
        network = resolved
        return resolved
    }

    @Suppress("DEPRECATION")
    private fun resolve(): Network? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        return runCatching {
            manager.allNetworks.firstOrNull { candidate ->
                val caps = runCatching { manager.getNetworkCapabilities(candidate) }.getOrNull()
                caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
            } ?: manager.activeNetwork
        }.getOrNull()
    }
}
