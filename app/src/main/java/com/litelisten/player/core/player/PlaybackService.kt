@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.litelisten.player.core.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 前台播放服务：息屏、锁屏、蓝牙按键都由 MediaSession 接管。 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var playerFactory: PlayerFactory

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playerFactory.create()
        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = MediaSession.Builder(this, player)
        if (sessionIntent != null) builder.setSessionActivity(sessionIntent)
        builder.setCallback(sessionCallback)
        mediaSession = builder.build()
    }

    /**
     * 无缝播放（跳过首尾静音）是 ExoPlayer 独有接口，MediaController 无法直接调用；
     * 通过自定义 SessionCommand 从 UI 透传到服务端真实播放器。
     */
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_SET_SKIP_SILENCE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SET_SKIP_SILENCE) {
                (session.player as? ExoPlayer)?.setSkipSilenceEnabled(
                    args.getBoolean(EXTRA_ENABLED, true),
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SET_SKIP_SILENCE = "com.litelisten.player.SET_SKIP_SILENCE"
        const val EXTRA_ENABLED = "enabled"
    }
}
