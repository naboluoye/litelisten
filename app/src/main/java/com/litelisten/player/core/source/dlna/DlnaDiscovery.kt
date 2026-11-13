package com.litelisten.player.core.source.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import com.litelisten.player.core.model.DiscoveredDevice
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.core.network.LocalNetworkHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

data class DlnaDevice(
    val uuid: String,
    val name: String,
    val controlUrl: String,
    val baseUrl: String,
)

/** SSDP M-SEARCH + 设备描述解析；不引入 cling / jupnp。 */
@Singleton
class DlnaDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val base: OkHttpClient,
    private val networkHolder: LocalNetworkHolder,
) {
    /** 设备描述（LOCATION）与控制请求都强制走局域网 Network，避免被系统默认路由甩到蜂窝。 */
    private val client: OkHttpClient
        get() = networkHolder.network?.socketFactory?.let { sf ->
            base.newBuilder().socketFactory(sf).build()
        } ?: base

    suspend fun discover(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val lock = runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.createMulticastLock("litelisten-ssdp").apply { setReferenceCounted(true); acquire() }
        }.getOrNull()
        try {
            // 先把真正的局域网 Network 记下来，后续 SSDP / 设备描述 / 控制请求都绑它。
            val network = resolveLocalNetwork().also { networkHolder.set(it) }
            val locations = LinkedHashSet<String>()
            DatagramSocket(0).use { socket ->
                // 蜂窝 + Wi-Fi 并存时，系统可能把组播发到蜂窝接口上；
                // 显式绑定到 Wi-Fi / 以太网 Network，否则同一个网络里也搜不到设备。
                bindToLocalNetwork(socket, network)
                socket.soTimeout = 350
                val target = InetAddress.getByName(SSDP_ADDRESS)
                repeat(2) {
                    SEARCH_TARGETS.forEach { st ->
                        val data = message(st).toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(data, data.size, target, SSDP_PORT))
                    }
                }
                // MX=2 允许设备在 2 秒内随机回复；旧实现每次 700ms 超时就 break，
                // 实际只等了不到 1 秒，响应稍慢的设备全部漏掉。
                val deadline = System.currentTimeMillis() + 4_000L
                while (System.currentTimeMillis() < deadline) {
                    val buffer = ByteArray(2048)
                    val packet = DatagramPacket(buffer, buffer.size)
                    val received = runCatching { socket.receive(packet); true }.getOrDefault(false)
                    if (!received) continue
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    LOCATION.find(text)?.groupValues?.get(1)?.let { locations.add(it) }
                }
            }
            locations.mapNotNull { location -> runCatching { parseDescription(location) }.getOrNull() }
                .distinctBy { it.uuid }
                .map { device ->
                    DiscoveredDevice(
                        id = device.uuid,
                        name = device.name,
                        type = SourceType.DLNA,
                        address = device.controlUrl,
                    )
                }
        } finally {
            runCatching { lock?.release() }
        }
    }

    fun configFor(device: DiscoveredDevice): SourceConfig {
        val uri = runCatching { Uri.parse(device.address) }.getOrNull()
        // 旧实现用 substringBefore("/_") 猜 base URL，控制地址不含 "/_" 时会把
        // 整个 SOAP 地址当成服务器地址；这里直接取 scheme://authority。
        val origin = uri?.let { "${it.scheme}://${it.authority}" }.orEmpty()
        return SourceConfig(
            id = "dlna-" + device.id.hashCode().toUInt().toString(16),
            type = SourceType.DLNA,
            name = device.name,
            url = origin,
            deviceUuid = device.id,
            controlUrl = device.address,
        )
    }

    @Suppress("DEPRECATION")
    private fun bindToLocalNetwork(socket: DatagramSocket, network: Network?) {
        if (network == null) return
        runCatching { network.bindSocket(socket) }
    }

    /**
     * 找到真正连着局域网的那张 Network（Wi-Fi / 以太网）。
     * 手机在「Wi-Fi 无互联网 + 开着蜂窝数据」时，系统默认网络是蜂窝，
     * 不显式选出局域网 Network 并绑定，内网 NAS 的控制地址就全部不可达。
     */
    @Suppress("DEPRECATION")
    private fun resolveLocalNetwork(): Network? {
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

    /** 用容错正则提取设备描述：命名空间前缀、标签大小写、缺失字段各家实现都不一样。 */
    private fun parseDescription(location: String): DlnaDevice {
        val request = Request.Builder().url(location).header("User-Agent", USER_AGENT).build()
        val xml = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP_${response.code}")
            response.body?.string().orEmpty()
        }
        val urlBase = tag(xml, "URLBase").takeIf { it.startsWith("http", true) }
            ?: location.substringBeforeLast('/', location)
        val controlUrl = SERVICE_BLOCK.findAll(xml)
            .firstOrNull { it.groupValues[1].contains("ContentDirectory", true) }
            ?.let { tag(it.groupValues[1], "controlURL") }
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("NO_CONTENT_DIRECTORY")
        return DlnaDevice(
            uuid = tag(xml, "UDN").ifBlank { location },
            name = tag(xml, "friendlyName").ifBlank { Uri.parse(location).host ?: location },
            controlUrl = resolve(location, urlBase, controlUrl),
            baseUrl = urlBase,
        )
    }

    private fun tag(xml: String, name: String): String {
        val regex = Regex(
            "(?is)<(?:[a-z0-9_-]+:)?${name}[^>]*>(.*?)</(?:[a-z0-9_-]+:)?${name}>",
            RegexOption.IGNORE_CASE,
        )
        return regex.find(xml)?.groupValues?.get(1)?.let(::unescape).orEmpty()
    }

    private fun unescape(value: String): String = value.trim()
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

    private fun resolve(location: String, base: String, path: String): String {
        if (path.startsWith("http", true)) return path
        val uri = Uri.parse(location)
        val host = "${uri.scheme}://${uri.authority}"
        return if (path.startsWith("/")) host + path else base.trimEnd('/') + "/" + path
    }

    private fun message(target: String): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: $target\r\n\r\n"

    private companion object {
        const val USER_AGENT = "LiteListen/1.0 UPnP/1.1 DLNADOC/1.50"
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        val LOCATION = Regex("(?i)LOCATION:\\s*(\\S+)")
        // 不只查 MediaServer：很多 NAS / 路由器只响应 ssdp:all 或 rootdevice。
        val SEARCH_TARGETS = listOf(
            "ssdp:all",
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaServer:1",
            "urn:schemas-upnp-org:service:ContentDirectory:1",
        )
        val SERVICE_BLOCK = Regex(
            "(?is)<(?:[a-z0-9_-]+:)?service[^>]*>(.*?)</(?:[a-z0-9_-]+:)?service>",
            RegexOption.IGNORE_CASE,
        )
    }
}
