package com.litelisten.player.core.source.dlna

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Xml
import com.litelisten.player.core.model.DiscoveredDevice
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale
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
    private val client: OkHttpClient,
) {
    suspend fun discover(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val lock = runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.createMulticastLock("litelisten-ssdp").apply { setReferenceCounted(true); acquire() }
        }.getOrNull()
        try {
            val locations = HashSet<String>()
            DatagramSocket().use { socket ->
                socket.soTimeout = 700
                val target = InetAddress.getByName("239.255.255.250")
                repeat(3) {
                    val data = MESSAGE.toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(data, data.size, target, 1900))
                    val until = System.currentTimeMillis() + 1_100L
                    while (System.currentTimeMillis() < until) {
                        val buffer = ByteArray(2048)
                        val packet = DatagramPacket(buffer, buffer.size)
                        val received = runCatching { socket.receive(packet); true }.getOrDefault(false)
                        if (!received) break
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        LOCATION.find(text)?.groupValues?.get(1)?.let { locations.add(it) }
                    }
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

    fun configFor(device: DiscoveredDevice): SourceConfig = SourceConfig(
        id = "dlna-" + device.id.hashCode().toUInt().toString(16),
        type = SourceType.DLNA,
        name = device.name,
        url = device.address.substringBefore("/_"),
        deviceUuid = device.id,
        controlUrl = device.address,
    )

    private fun parseDescription(location: String): DlnaDevice {
        val request = Request.Builder().url(location).header("User-Agent", USER_AGENT).build()
        val xml = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP_${response.code}")
            response.body?.string().orEmpty()
        }
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        var friendlyName = ""
        var udn = ""
        var urlBase = location.substringBeforeLast('/', location)
        val controlUrls = ArrayList<String>()
        var inContentDirectory = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':')?.lowercase(Locale.ROOT).orEmpty()
            if (event == XmlPullParser.START_TAG && tag == "servicetype") {
                inContentDirectory = parser.nextText().contains("ContentDirectory", true)
            } else if (event == XmlPullParser.START_TAG) {
                when (tag) {
                    "friendlyname" -> friendlyName = parser.nextText()
                    "udn" -> udn = parser.nextText()
                    "urlbase" -> urlBase = parser.nextText()
                    "controlurl" -> if (inContentDirectory) controlUrls.add(parser.nextText())
                }
            }
            event = parser.next()
        }
        if (controlUrls.isEmpty()) throw IllegalStateException("NO_CONTENT_DIRECTORY")
        val control = resolve(location, urlBase, controlUrls.first())
        return DlnaDevice(
            uuid = udn.ifBlank { location },
            name = friendlyName.ifBlank { Uri.parse(location).host ?: location },
            controlUrl = control,
            baseUrl = location.substringBeforeLast('/', location),
        )
    }

    private fun resolve(location: String, base: String, path: String): String {
        if (path.startsWith("http", true)) return path
        val host = Uri.parse(location).let { "${it.scheme}://${it.authority}" }
        return if (path.startsWith("/")) host + path else base.trimEnd('/') + "/" + path
    }

    private companion object {
        const val USER_AGENT = "LiteListen/1.0 UPnP/1.1 DLNADOC/1.50"
        val LOCATION = Regex("(?i)LOCATION:\\s*(\\S+)")
        const val MESSAGE =
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n"
    }
}
