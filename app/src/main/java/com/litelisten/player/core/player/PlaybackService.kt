package com.litelisten.player.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
        mediaSession = builder.build()
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
}
