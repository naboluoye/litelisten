package com.litelisten.player.core.source.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.litelisten.player.core.common.LanAddress
import com.litelisten.player.core.model.DiscoveredDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫描本机 Wi-Fi 子网里开放文件共享端口的主机。
 * SSDP 只能发现 UPnP MediaServer，普通 Windows 电脑 / NAS 的 SMB、WebDAV
 * 不会响应 M-SEARCH，所以必须另做一次端口探测才能「发现网络里的电脑」。
 *
 * 红线：目标地址全部由本机私网子网推导，并在连接前逐个经 LanAddress 校验；
 * 不解析任何域名、不访问任何公网地址，只做 TCP connect 探测和可选的 NBNS 名称查询。
 */
@Singleton
class LanHostScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private data class Subnet(val self: Int, val prefix: Int)

    suspend fun scan(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val subnet = localSubnet() ?: return@withContext emptyList()
        val network = localNetwork()
        val hosts = LanScanPlan.hosts(subnet.self, subnet.prefix)
        val pool = Executors.newFixedThreadPool(48)
        try {
            val futures = hosts.map { ip -> pool.submit<DiscoveredDevice?> { probe(ip, network) } }
            // 253 台主机 / 48 线程 ≈ 6 轮，每轮最坏 2.2s；超时必须覆盖最后一轮，
            // 否则排在队尾的主机结果会被静默丢弃。
            futures.mapNotNull { future ->
                runCatching { future.get(30, TimeUnit.SECONDS) }.getOrNull()
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /** 给用户看的扫描范围，例如 192.168.1.0/24；拿不到说明没连上本地网络。 */
    fun subnetLabel(): String? {
        val subnet = localSubnet() ?: return null
        val bits = subnet.prefix.coerceIn(24, 30)
        val mask = (0xffffffffL shl (32 - bits)) and 0xffffffffL
        val network = (subnet.self.toLong() and 0xffffffffL) and mask
        return LanScanPlan.intToIp(network) + "/" + bits
    }

    private fun probe(ip: String, network: Network?): DiscoveredDevice? {
        if (!LanAddress.isPrivateHost(ip)) return null
        for (port in LanScanPlan.PROBE_PORTS) {
            if (!isOpen(ip, port, network)) continue
            val name = netBiosName(ip, network) ?: ip
            return DiscoveredDevice(
                id = "lan-$ip-$port",
                name = name,
                type = LanScanPlan.typeOf(port),
                address = ip,
                detail = port.toString(),
                port = port,
            )
        }
        return null
    }

    private fun isOpen(ip: String, port: Int, network: Network?): Boolean {
        val socket = runCatching { network?.socketFactory?.createSocket() ?: Socket() }
            .getOrElse { Socket() }
        return try {
            socket.connect(InetSocketAddress(ip, port), LanScanPlan.CONNECT_TIMEOUT_MS)
            true
        } catch (error: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    /** 尽力拿 Windows / NAS 的 NetBIOS 名字，拿不到就显示 IP。UDP 137，仅发往已校验的私网 IP。 */
    private fun netBiosName(ip: String, network: Network?): String? {
        val socket = DatagramSocket()
        return try {
            // 裸 Socket 会走系统默认网络；Wi-Fi 没有互联网时默认是蜂窝，
            // 内网 IP 全部不可达。必须显式绑到 Wi-Fi / 以太网 Network。
            if (network != null) runCatching { network.bindSocket(socket) }
            socket.soTimeout = LanScanPlan.NBNS_TIMEOUT_MS
            val query = nbnsQuery()
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(ip), 137))
            val buffer = ByteArray(512)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            parseNbns(buffer, packet.length)
        } catch (error: Exception) {
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun nbnsQuery(): ByteArray {
        val bytes = ByteArray(50)
        var p = 0
        bytes[p++] = 0x13
        bytes[p++] = 0x37
        bytes[p++] = 0
        bytes[p++] = 0
        bytes[p++] = 0
        bytes[p++] = 1
        repeat(6) { bytes[p++] = 0 }
        bytes[p++] = 0x20
        for (ch in WILDCARD_NAME) bytes[p++] = ch.code.toByte()
        bytes[p++] = 0
        bytes[p++] = 0
        bytes[p++] = 0x20
        bytes[p++] = 0
        bytes[p++] = 1
        return bytes
    }

    private fun parseNbns(data: ByteArray, length: Int): String? {
        // NBNS Node Status: 12 header + 34 question + 4 qtype/class + 34 answer name + 10 RR header
        val rdata = 12 + 34 + 4 + 34 + 10
        if (length <= rdata) return null
        val count = (data[rdata].toInt() and 0xff).coerceAtMost(16)
        var offset = rdata + 1
        repeat(count) {
            if (offset + 16 > length) return null
            val type = data[offset + 15].toInt() and 0xff
            val name = data.copyOfRange(offset, offset + 15).toString(Charsets.US_ASCII).trim()
            if ((type == 0x00 || type == 0x20) && name.isNotEmpty() &&
                name.all { it.isLetterOrDigit() || it == '-' }
            ) {
                return name
            }
            offset += 18
        }
        return null
    }

    /**
     * 找到真正连着局域网的那张 Network。Wi-Fi 没有互联网时系统会默认走蜂窝，
     * 不显式绑定就会把 192.168.x.x 的探测全部发到蜂窝网络上而一台也扫不到。
     */
    @Suppress("DEPRECATION")
    private fun localNetwork(): Network? {
        val manager = connectivity() ?: return null
        val wifi = runCatching {
            manager.allNetworks.firstOrNull { network ->
                val caps = runCatching { manager.getNetworkCapabilities(network) }.getOrNull()
                caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
            }
        }.getOrNull()
        return wifi ?: runCatching { manager.activeNetwork }.getOrNull()
    }

    private fun connectivity(): ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @Suppress("DEPRECATION")
    private fun localSubnet(): Subnet? {
        val manager = connectivity()
        // 优先选 Wi-Fi / 以太网，避免手机挂 VPN 时把 VPN 网段当成局域网去扫。
        val network = localNetwork()
        val properties = network?.let {
            runCatching { manager?.getLinkProperties(it) }.getOrNull()
        }
        properties?.linkAddresses?.forEach { link ->
            val address = link.address
            if (isUsableLanAddress(address)) {
                return Subnet(ByteBuffer.wrap(address.address).int, link.prefixLength)
            }
        }
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
            ?: return null
        for (nic in interfaces) {
            for (address in nic.inetAddresses) {
                if (isUsableLanAddress(address)) {
                    return Subnet(ByteBuffer.wrap(address.address).int, LanScanPlan.DEFAULT_PREFIX)
                }
            }
        }
        return null
    }

    private fun isUsableLanAddress(address: InetAddress?): Boolean =
        address is Inet4Address &&
            !address.isLoopbackAddress &&
            !address.isLinkLocalAddress &&
            LanAddress.isPrivateHost(address.hostAddress.orEmpty())

    private companion object {
        val WILDCARD_NAME = "CK" + "CA".repeat(15)
    }
}
