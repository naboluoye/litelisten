package com.litelisten.player.core.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音效引擎的单例宿主：生命周期跟应用绑定，不随均衡器页面（ViewModel）销毁。
 * 以前 Equalizer/BassBoost 建在 ViewModel 里，返回其它页面时 onCleared() 直接 release，
 * 于是「打开音效，返回就自动关闭」。现在引擎搬到这里，页面退出只销毁代理 ViewModel，
 * 音效对象继续挂在播放会话上，回到播放页仍然生效。
 */
@Singleton
class EqualizerController @Inject constructor(
    private val audioSession: AudioSessionHolder,
) {
    val sessionId: StateFlow<Int> = audioSession.sessionId

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var attachedSession = -1

    private val _supported = MutableStateFlow(false)
    val supported: StateFlow<Boolean> = _supported.asStateFlow()
    private val _bands = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val bands: StateFlow<List<Pair<Int, Int>>> = _bands.asStateFlow()
    private val _levels = MutableStateFlow<List<Int>>(emptyList())
    val levels: StateFlow<List<Int>> = _levels.asStateFlow()
    private val _bandRange = MutableStateFlow(-1500..1500)
    val bandRange: StateFlow<IntRange> = _bandRange.asStateFlow()
    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _bass = MutableStateFlow(0)
    val bass: StateFlow<Int> = _bass.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // 会话 ID 由播放服务发布；即使音效页面没打开，也要在会话重建后自动把音效挂回去，
        // 否则用户开着音效听歌、服务一重建声音就没效果了。
        scope.launch {
            audioSession.sessionId
                .filter { it > 0 }
                .distinctUntilChanged()
                .collect { init(it) }
        }
    }

    /**
     * 会话 ID 变化（播放服务重建）时重新挂接音效，否则滑块改的是一条废弃会话。
     * 重新挂接时把之前保存的开启状态、频段、低音重新写回新引擎。
     */
    fun init(session: Int) {
        if (session <= 0) return
        if (equalizer != null && attachedSession == session) return
        val wasEnabled = _enabled.value
        val savedLevels = _levels.value
        val savedBass = _bass.value
        releaseEngine()
        attachedSession = session
        runCatching {
            val eq = Equalizer(0, session)
            equalizer = eq
            val range = eq.bandLevelRange
            _bandRange.value = range[0].toInt()..range[1].toInt()
            val restoredBands = (0 until eq.numberOfBands).map { band ->
                val center = (eq.getCenterFreq(band.toShort()) / 1000)
                val level = savedLevels.getOrNull(band) ?: eq.getBandLevel(band.toShort()).toInt()
                runCatching { eq.setBandLevel(band.toShort(), level.toShort()) }
                center to level
            }
            _bands.value = restoredBands
            _levels.value = restoredBands.map { it.second }
            _presets.value = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            bassBoost = runCatching { BassBoost(0, session) }.getOrNull()
            bassBoost?.let { runCatching { it.setStrength(savedBass.toShort()) } }
            _supported.value = true
            // 关键：会话重建后恢复开启状态，而不是沿用新引擎默认的 false。
            setEnabled(wasEnabled)
        }.onFailure { _supported.value = false }
    }

    fun setEnabled(value: Boolean) {
        runCatching { equalizer?.enabled = value; bassBoost?.enabled = value }
        _enabled.value = value
    }

    fun setBand(index: Int, level: Int) {
        runCatching { equalizer?.setBandLevel(index.toShort(), level.toShort()) }
        _levels.value = _levels.value.toMutableList().also { if (index in it.indices) it[index] = level }
    }

    fun setPreset(index: Int) {
        runCatching {
            equalizer?.usePreset(index.toShort())
            _levels.value = (0 until (equalizer?.numberOfBands ?: 0)).map {
                equalizer?.getBandLevel(it.toShort())?.toInt() ?: 0
            }
        }
    }

    fun setBass(strength: Int) {
        runCatching { bassBoost?.setStrength(strength.coerceIn(0, 1000).toShort()) }
        _bass.value = strength
    }

    private fun releaseEngine() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        _supported.value = false
    }
}
