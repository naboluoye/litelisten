package com.litelisten.player.core.source.api

import com.litelisten.player.core.model.SourceType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

/** 由 SourceModule 统一注册三个协议实现，避免重复绑定。 */
@Singleton
class SourceRegistry {
    private val providers = ConcurrentHashMap<SourceType, SourceProvider>()

    fun register(provider: SourceProvider) {
        providers[provider.type] = provider
    }

    fun get(type: SourceType): SourceProvider? = providers[type]

    fun registeredTypes(): Set<SourceType> = providers.keys.toSet()
}
