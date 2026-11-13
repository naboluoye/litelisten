package com.litelisten.player.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 把播放器真实使用的 audioSessionId 交给音效模块，避免均衡器挂到全局混音上不起作用。 */
@Singleton
class AudioSessionHolder @Inject constructor() {
    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()

    fun publish(id: Int) {
        if (id > 0) _sessionId.value = id
    }
}
