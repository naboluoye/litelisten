package com.litelisten.player.ui.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.lifecycle.ViewModel
import com.litelisten.player.core.player.AudioSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    audioSession: AudioSessionHolder,
) : ViewModel() {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var attachedSession = -1

    val sessionId: StateFlow<Int> = audioSession.sessionId

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

    /** 会话 ID 变化（播放服务重建）时重新挂接音效，否则滑块改的是一条废弃会话。 */
    fun init(session: Int) {
        if (equalizer != null && attachedSession == session) return
        release()
        attachedSession = session
        runCatching {
            val eq = Equalizer(0, session)
            equalizer = eq
            val range = eq.bandLevelRange
            _bandRange.value = range[0].toInt()..range[1].toInt()
            _bands.value = (0 until eq.numberOfBands).map { band ->
                (eq.getCenterFreq(band.toShort()) / 1000) to eq.getBandLevel(band.toShort()).toInt()
            }
            _levels.value = _bands.value.map { it.second }
            _presets.value = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            _enabled.value = eq.enabled
            bassBoost = runCatching { BassBoost(0, session) }.getOrNull()
            _supported.value = true
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

    private fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        _supported.value = false
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }
}
