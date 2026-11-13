package com.litelisten.player.core.source.api

import com.litelisten.player.core.model.SourceType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** P3/P4 由 SourceModule 注入各协议实现；P0 保持空注册表，不抛异常占位。 */
@Singleton
class SourceRegistry @Inject constructor() {
    private val providers = ConcurrentHashMap<SourceType, SourceProvider>()

    fun register(provider: SourceProvider) {
        providers[provider.type] = provider
    }

    fun get(type: SourceType): SourceProvider? = providers[type]

    fun registeredTypes(): Set<SourceType> = providers.keys.toSet()
}
