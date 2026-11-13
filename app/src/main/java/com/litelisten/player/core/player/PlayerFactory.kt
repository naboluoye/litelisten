@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.litelisten.player.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.litelisten.player.core.datastore.RuntimeConfig
import com.litelisten.player.core.source.smb.SmbConnectionPool
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class PlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("streaming") private val streamingClient: OkHttpClient,
    private val smbPool: SmbConnectionPool,
    private val runtime: RuntimeConfig,
    private val audioSession: AudioSessionHolder,
) {
    fun create(): ExoPlayer {
        val http = OkHttpDataSource.Factory(streamingClient)
        val upstream = LiteListenDataSourceFactory(
            DefaultDataSource.Factory(context, http),
            smbPool,
        )
        val bufferMs = runtime.bufferSeconds * 1_000
        val sessionId = Util.generateAudioSessionIdV21(context)
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(upstream))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(runtime.pauseOnHeadsetUnplug)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        bufferMs,
                        (bufferMs * 4L).coerceAtMost(300_000L).toInt(),
                        1_500,
                        2_500,
                    )
                    .build(),
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        // 播放尚未开始时指定会话 ID，均衡器就能挂到真正输出音频的那条会话上。
        runCatching { player.setAudioSessionId(sessionId) }
        audioSession.publish(sessionId)
        return player
    }
}
