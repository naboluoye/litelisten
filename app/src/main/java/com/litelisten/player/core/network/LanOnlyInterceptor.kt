package com.litelisten.player.core.network

import com.litelisten.player.core.common.LanAddress
import com.litelisten.player.core.common.NonLanAddressException
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** 所有 OkHttp 出口的统一闸口：目标不是私网地址就直接拒绝，连请求都不发出去。 */
@Singleton
class LanOnlyInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (!LanAddress.isPrivateHost(host)) throw NonLanAddressException(host)
        return chain.proceed(chain.request())
    }
}
