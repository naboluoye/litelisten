package com.litelisten.player.core.network

import com.litelisten.player.core.datastore.RuntimeConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpProvider @Inject constructor(
    private val lanOnlyInterceptor: LanOnlyInterceptor,
    private val authInterceptor: AuthInterceptor,
    private val runtime: RuntimeConfig,
) {
    fun base(): OkHttpClient {
        val timeout = runtime.connectTimeoutSeconds.toLong()
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(lanOnlyInterceptor)
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout.coerceAtLeast(15L), TimeUnit.SECONDS)
            .writeTimeout(timeout.coerceAtLeast(15L), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 长音频流播：callTimeout(0) 避免一次播放被总时长上限掐断。 */
    fun streaming(): OkHttpClient = base().newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
