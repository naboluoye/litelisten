package com.litelisten.player.core.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.litelisten.player.core.datastore.QueueSnapshot
import com.litelisten.player.core.datastore.QueueStore
import com.litelisten.player.core.datastore.RuntimeConfig
import com.litelisten.player.core.datastore.SettingsKeys
import com.litelisten.player.core.datastore.SettingsRepository
import com.litelisten.player.core.model.PlaybackController
import com.litelisten.player.core.model.PlaybackError
import com.litelisten.player.core.model.PlayerUiState
import com.litelisten.player.core.model.RepeatMode
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.core.model.Track
import com.litelisten.player.core.source.api.SourceReadiness
import com.litelisten.player.data.repository.TrackRepositoryImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** UI 只通过 MediaController 操作播放器；Activity 不持有 ExoPlayer。 */
@Singleton
class PlaybackControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackRepository: TrackRepositoryImpl,
    private val queueStore: QueueStore,
    private val settings: SettingsRepository,
    private val runtime: RuntimeConfig,
    private val sourceReadiness: SourceReadiness,
) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFlow = MutableStateFlow<MediaController?>(null)
    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var failureCount = 0

    init { connect() }

    private fun connect(restoreQueueOnSuccess: Boolean = true) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    _state.value = _state.value.copy(error = PlaybackError.PlayerNotReady)
                    return@addListener
                }
                controller.addListener(playerListener)
                controllerFlow.value = controller
                if (restoreQueueOnSuccess) scope.launch { restoreQueue() }
                updateState()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = updateState()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState()
            if (isPlaying) startTicker() else stopTicker()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            failureCount = 0
            updateState()
            mediaItem?.mediaId?.let { id -> scope.launch { trackRepository.markPlayed(id) } }
            persistQueue()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateState()
            persistQueue()
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            updateState()
            persistQueue()
        }
        override fun onPlayerError(error: PlaybackException) {
            failureCount++
            _state.value = _state.value.copy(error = mapPlaybackError(error))
            scope.launch {
                val skip = settings.observe(SettingsKeys.SKIP_ON_ERROR_KEY, true).first()
                if (skip && failureCount < 3) {
                    controllerFlow.value?.let { c ->
                        c.seekToNextMediaItem()
                        c.prepare()
                        c.play()
                    }
                } else {
                    controllerFlow.value?.pause()
                }
            }
        }
    }

    /** 插入队列但不打断当前播放；UI 通过 PlaybackControllerImpl 直接调用。 */
    fun playNext(track: Track) {
        val controller = requireController() ?: return
        val index = (controller.currentMediaItemIndex + 1).coerceAtLeast(0)
        controller.addMediaItem(index, buildMediaItem(track))
        _queue.value = _queue.value.toMutableList().also { list ->
            list.add(index.coerceAtMost(list.size), track)
        }
        persistQueue()
    }

    fun addToQueue(track: Track) {
        val controller = requireController() ?: return
        controller.addMediaItem(buildMediaItem(track))
        _queue.value = _queue.value + track
        persistQueue()
    }

    override suspend fun play(track: Track, context: List<Track>, startIndex: Int?) {
        val items = context.ifEmpty { listOf(track) }
        _queue.value = items
        _state.value = _state.value.copy(error = null)
        awaitSourcesIfNeeded(items)
        val controller = awaitController()
        if (controller == null) {
            _state.value = _state.value.copy(error = PlaybackError.PlayerNotReady)
            connect(restoreQueueOnSuccess = false)
            return
        }
        val index = (startIndex ?: items.indexOfFirst { it.id == track.id }).coerceIn(0, items.lastIndex)
        controller.setMediaItems(items.map { buildMediaItem(it) }, index, 0L)
        controller.prepare()
        controller.play()
        persistQueue()
    }

    /** 网络源冷启动 hydrate 是异步的，播放前等它完成，避免连接池 / 认证头还没注册。 */
    private suspend fun awaitSourcesIfNeeded(items: List<Track>) {
        if (items.any { it.sourceType != SourceType.LOCAL && it.sourceType != SourceType.SAF }) {
            sourceReadiness.await()
        }
    }

    /** 连接尚未建立时不再静默丢弃操作：明确报错，并尝试重连以便下次点击生效。 */
    private fun requireController(): MediaController? = controllerFlow.value ?: run {
        _state.value = _state.value.copy(error = PlaybackError.PlayerNotReady)
        connect(restoreQueueOnSuccess = false)
        null
    }

    override fun playPause() {
        val controller = requireController() ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
            controller.play()
        }
        _state.value = _state.value.copy(error = null)
    }

    override fun next() {
        val controller = requireController() ?: return
        controller.seekToNextMediaItem()
        _state.value = _state.value.copy(error = null)
    }

    override fun previous() {
        val controller = controllerFlow.value ?: return
        if (controller.currentPosition > 3_000L) controller.seekTo(0L) else controller.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        controllerFlow.value?.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setShuffle(enabled: Boolean) {
        controllerFlow.value?.shuffleModeEnabled = enabled
        updateState()
        persistQueue()
    }

    override fun setRepeat(mode: RepeatMode) {
        controllerFlow.value?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        updateState()
        persistQueue()
    }

    override fun moveInQueue(from: Int, to: Int) {
        val controller = controllerFlow.value ?: return
        val items = _queue.value.toMutableList()
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        _queue.value = items
        controller.moveMediaItem(from, to)
        persistQueue()
    }

    override fun removeFromQueue(index: Int) {
        val controller = controllerFlow.value ?: return
        if (index !in _queue.value.indices) return
        _queue.value = _queue.value.toMutableList().also { it.removeAt(index) }
        controller.removeMediaItem(index)
        persistQueue()
    }

    override fun clearQueue() {
        controllerFlow.value?.clearMediaItems()
        _queue.value = emptyList()
        scope.launch { queueStore.clear() }
        updateState()
    }

    override fun setSpeed(speed: Float) {
        val value = speed.coerceIn(0.5f, 2f)
        controllerFlow.value?.setPlaybackSpeed(value)
        updateState()
        persistQueue()
    }

    override fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _state.value = _state.value.copy(sleepTimerRemainingMs = null)
            return
        }
        val endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = scope.launch {
            while (isActive) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0L) break
                _state.value = _state.value.copy(sleepTimerRemainingMs = left)
                delay(1_000L)
            }
            val controller = controllerFlow.value
            if (runtime.fadeEnabled) {
                for (step in 10 downTo 1) {
                    controller?.setVolume(step / 10f)
                    delay(1_000L)
                }
            }
            controller?.pause()
            controller?.setVolume(1f)
            _state.value = _state.value.copy(sleepTimerRemainingMs = null)
        }
    }

    override fun stop() {
        val controller = controllerFlow.value
        controller?.stop()
        _queue.value = emptyList()
        _state.value = PlayerUiState()
        scope.launch { queueStore.clear() }
    }

    private suspend fun awaitController(): MediaController? =
        controllerFlow.value ?: withTimeoutOrNull(5_000L) { controllerFlow.filterNotNull().first() }

    private fun buildMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(
                track.artworkKey?.let {
                    Uri.fromFile(File(context.cacheDir, "artwork/$it"))
                },
            )
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.uri))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun updateState() {
        val controller = controllerFlow.value ?: return
        val mediaId = controller.currentMediaItem?.mediaId
        val current = _queue.value.firstOrNull { it.id == mediaId }
        val duration = controller.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: current?.durationMs ?: 0L
        _state.value = _state.value.copy(
            current = current,
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedMs = controller.bufferedPosition.coerceAtLeast(0L),
            shuffle = controller.shuffleModeEnabled,
            repeat = when (controller.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            speed = controller.playbackParameters.speed,
        )
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                updateState()
                delay(500L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        updateState()
    }

    private fun persistQueue() {
        val controller = controllerFlow.value ?: return
        val items = _queue.value
        if (items.isEmpty()) return
        scope.launch {
            queueStore.save(
                QueueSnapshot(
                    ids = items.map { it.id },
                    index = controller.currentMediaItemIndex.coerceAtLeast(0),
                    positionMs = controller.currentPosition.coerceAtLeast(0L),
                    shuffle = controller.shuffleModeEnabled,
                    repeat = _state.value.repeat.name,
                    speed = _state.value.speed,
                ),
            )
        }
    }

    private suspend fun restoreQueue() {
        if (!settings.observe(SettingsKeys.RESUME_ON_START_KEY, true).first()) return
        val snapshot = queueStore.restore() ?: return
        val byId = trackRepository.byIds(snapshot.ids).associateBy { it.id }
        val ordered = snapshot.ids.mapNotNull { byId[it] }
        if (ordered.isEmpty()) return
        _queue.value = ordered
        awaitSourcesIfNeeded(ordered)
        val controller = awaitController() ?: return
        controller.setMediaItems(
            ordered.map { buildMediaItem(it) },
            snapshot.index.coerceIn(0, ordered.lastIndex),
            snapshot.positionMs,
        )
        controller.prepare()
        controller.setPlaybackSpeed(
            settings.observe(SettingsKeys.PLAYBACK_SPEED_KEY, snapshot.speed).first(),
        )
        controller.shuffleModeEnabled = snapshot.shuffle
        controller.repeatMode = when (runCatching { RepeatMode.valueOf(snapshot.repeat) }.getOrDefault(RepeatMode.ALL)) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        if (runtime.autoPlayOnStart) controller.play()
        updateState()
    }

    private fun mapPlaybackError(error: PlaybackException): PlaybackError = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackError.FileMissing
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.NetworkLost
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> PlaybackError.UnsupportedFormat
        else -> PlaybackError.Other(error.errorCodeName)
    }
}
