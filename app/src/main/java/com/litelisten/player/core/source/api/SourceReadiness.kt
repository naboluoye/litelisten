package com.litelisten.player.core.source.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 冷启动后网络源要异步 hydrate（重建 SMB 连接池、WebDAV / DLNA 认证头）。
 * 播放网络曲目之前必须等它完成，否则竞态下会拿着还没注册的配置直接失败。
 */
@Singleton
class SourceReadiness @Inject constructor() {
    private val hydrated = CompletableDeferred<Unit>()

    fun markReady() {
        if (!hydrated.isCompleted) hydrated.complete(Unit)
    }

    /** 带兜底超时：hydrate 即使异常卡住，也不会让播放永远无响应。 */
    suspend fun await(timeoutMs: Long = 4_000L) {
        withTimeoutOrNull(timeoutMs) { hydrated.await() }
    }
}
