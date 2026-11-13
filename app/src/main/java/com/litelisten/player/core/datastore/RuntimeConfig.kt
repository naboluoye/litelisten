package com.litelisten.player.core.datastore

import com.litelisten.player.core.datastore.SettingsKeys.AUTO_PLAY_ON_START_KEY
import com.litelisten.player.core.datastore.SettingsKeys.BUFFER_SECONDS_KEY
import com.litelisten.player.core.datastore.SettingsKeys.CACHE_LIMIT_MB_KEY
import com.litelisten.player.core.datastore.SettingsKeys.CONNECT_TIMEOUT_S_KEY
import com.litelisten.player.core.datastore.SettingsKeys.FADE_ENABLED_KEY
import com.litelisten.player.core.datastore.SettingsKeys.HEADSET_UNPLUG_PAUSE_KEY
import com.litelisten.player.core.datastore.SettingsKeys.WIFI_ONLY_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 DataStore 的设置镜像成可被同步代码读取的快照。
 * OkHttp / ExoPlayer / 拦截器在构造时不能挂起，因此这里在 Application 生命周期内常驻收集。
 * 修改设置后对「下一次新建的连接 / 播放器」生效，不打断正在播放的音频。
 */
@Singleton
class RuntimeConfig @Inject constructor(
    settings: SettingsRepository,
) {
    @Volatile var connectTimeoutSeconds: Int = 10
        private set
    @Volatile var bufferSeconds: Int = 15
        private set
    @Volatile var cacheLimitMb: Int = 512
        private set
    @Volatile var wifiOnly: Boolean = false
        private set
    @Volatile var pauseOnHeadsetUnplug: Boolean = true
        private set
    @Volatile var fadeEnabled: Boolean = true
        private set
    @Volatile var autoPlayOnStart: Boolean = false
        private set

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            settings.all.collect { prefs ->
                connectTimeoutSeconds = (prefs[CONNECT_TIMEOUT_S_KEY] ?: 10).coerceIn(3, 60)
                bufferSeconds = (prefs[BUFFER_SECONDS_KEY] ?: 15).coerceIn(5, 60)
                cacheLimitMb = (prefs[CACHE_LIMIT_MB_KEY] ?: 512).coerceIn(64, 2048)
                wifiOnly = prefs[WIFI_ONLY_KEY] ?: false
                pauseOnHeadsetUnplug = prefs[HEADSET_UNPLUG_PAUSE_KEY] ?: true
                fadeEnabled = prefs[FADE_ENABLED_KEY] ?: true
                autoPlayOnStart = prefs[AUTO_PLAY_ON_START_KEY] ?: false
            }
        }
    }
}
