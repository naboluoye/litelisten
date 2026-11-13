package com.litelisten.player.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.litelisten.player.core.common.LanAddress
import com.litelisten.player.core.common.NonLanAddressException
import com.litelisten.player.core.datastore.RuntimeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 所有 OkHttp 出口的统一闸口：目标不是私网地址就直接拒绝，连请求都不发出去。 */
@Singleton
class LanOnlyInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: RuntimeConfig,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (!LanAddress.isPrivateHost(host)) throw NonLanAddressException(host)
        if (runtime.wifiOnly && !onWifi()) throw IOException("WIFI_ONLY")
        return chain.proceed(chain.request())
    }

    private fun onWifi(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val caps = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
            ?: return true
        // 以太网 / VPN 也视为可信的本地网络；只拦截蜂窝数据。
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
