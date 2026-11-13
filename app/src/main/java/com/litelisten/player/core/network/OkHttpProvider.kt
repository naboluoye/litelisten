package com.litelisten.player.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpProvider @Inject constructor(
    private val lanOnlyInterceptor: LanOnlyInterceptor,
) {
    fun base(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(lanOnlyInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * P4 播放专用：OkHttpDataSource 天然支持 Range 拖动。
     * callTimeout(0) 是关键——长音频流式播放不能被"单次 call 总时长"提前掐断。
     */
    fun streaming(): OkHttpClient = base().newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
