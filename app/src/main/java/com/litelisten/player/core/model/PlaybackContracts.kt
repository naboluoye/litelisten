package com.litelisten.player.core.model

import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    val state: StateFlow<PlayerUiState>
    val queue: StateFlow<List<Track>>

    suspend fun play(track: Track, context: List<Track> = listOf(track), startIndex: Int? = null)
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setShuffle(enabled: Boolean)
    fun setRepeat(mode: RepeatMode)
    fun moveInQueue(from: Int, to: Int)
    fun removeFromQueue(index: Int)
    fun clearQueue()
    fun setSpeed(speed: Float)
    fun setSleepTimer(minutes: Int?)
    fun stop()
}

/** 播放链路实际解码出的音轨规格；压缩格式未必提供 bitDepth，允许为 null。 */
data class AudioFormatInfo(
    val mimeType: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channelCount: Int? = null,
    val bitrateKbps: Int? = null,
    val floatOutput: Boolean = false,
)

data class PlayerUiState(
    val current: Track? = null,
    val audioFormat: AudioFormatInfo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.ALL,
    val speed: Float = 1f,
    val error: PlaybackError? = null,
    val sleepTimerRemainingMs: Long? = null,
)

sealed interface PlaybackError {
    data object UnsupportedFormat : PlaybackError
    data object FileMissing : PlaybackError
    data object NetworkLost : PlaybackError
    data object AuthFailed : PlaybackError
    data object PlayerNotReady : PlaybackError
    data class Other(val message: String) : PlaybackError
}
