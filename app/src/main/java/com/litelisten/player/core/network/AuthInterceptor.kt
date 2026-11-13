package com.litelisten.player.core.network

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 按 host:port 注入认证头，凭据只在内存里，绝不落到 URI 或日志。 */
@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {
    private val headers = ConcurrentHashMap<String, Map<String, String>>()

    fun register(host: String, port: Int, username: String?, password: String?) {
        val key = "$host:$port"
        if (username.isNullOrBlank() || password == null) {
            headers.remove(key)
        } else {
            headers[key] = mapOf("Authorization" to Credentials.basic(username, password))
        }
    }

    fun registerHeaders(host: String, port: Int, values: Map<String, String>) {
        headers["$host:$port"] = values
    }

    fun clear(host: String, port: Int) {
        headers.remove("$host:$port")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val extra = headers["${request.url.host}:${request.url.port}"] ?: return chain.proceed(request)
        if (extra.isEmpty()) return chain.proceed(request)
        val builder = request.newBuilder()
        extra.forEach { (name, value) ->
            if (request.header(name).isNullOrBlank()) builder.header(name, value)
        }
        return chain.proceed(builder.build())
    }
}
