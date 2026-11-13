package com.litelisten.player.core.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.litelisten.player.R
import com.litelisten.player.core.database.TrackDao
import com.litelisten.player.core.database.TrackEntity
import com.litelisten.player.core.datastore.SettingsKeys
import com.litelisten.player.core.datastore.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val scanned: Int, val total: Int) : ScanState
    data class Done(val total: Int) : ScanState
    data class Failed(@StringRes val messageRes: Int) : ScanState
}

/** 扫描入口统一在这里；增量比对靠稳定 id，消失的文件软删除以保留收藏。 */
@Singleton
class LibraryScanner @Inject constructor(
    private val trackDao: TrackDao,
    private val mediaStoreScanner: MediaStoreScanner,
    private val safScanner: SafScanner,
    private val artworkCache: ArtworkCache,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var currentJob: Job? = null
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** 所有入口统一从设置读取「显示短音频」，避免不同页面扫描结果不一致。 */
    fun scanLocal() {
        currentJob?.cancel()
        currentJob = scope.launch {
            val showShort = settings.observe(SettingsKeys.SHOW_SHORT_AUDIO_KEY, false).first()
            mutex.withLock { runScanLocked(showShort) }
        }
    }

    /**
     * 装完第一次授权后自动扫一次：曲库为空时不等用户自己去找「扫描」按钮。
     * 权限与「本地曲库是否为空」两个条件都在内部判断，调用点可以无条件、重复调用。
     */
    fun scanLocalIfEmpty() {
        if (_state.value is ScanState.Running) return
        if (!hasAudioPermission()) return
        currentJob?.cancel()
        currentJob = scope.launch {
            val existing = runCatching { trackDao.countLocal() }.getOrDefault(-1)
            if (existing > 0) return@launch
            val showShort = settings.observe(SettingsKeys.SHOW_SHORT_AUDIO_KEY, false).first()
            mutex.withLock { runScanLocked(showShort) }
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** 重建索引：先全量重扫，成功后才清空并替换，扫描失败不会留下空曲库。 */
    fun rebuild() {
        currentJob?.cancel()
        currentJob = scope.launch {
            val showShort = settings.observe(SettingsKeys.SHOW_SHORT_AUDIO_KEY, false).first()
            mutex.withLock {
                _state.value = ScanState.Running(0, 0)
                runCatching {
                    val scanned = mediaStoreScanner.scan(showShort) { count ->
                        _state.value = ScanState.Running(count, count)
                    }
                    trackDao.deleteAll()
                    replace("local", scanned.map { it.entity })
                    _state.value = ScanState.Done(scanned.size)
                    fillArtwork(scanned)
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.value = ScanState.Failed(R.string.library_scan_failed)
                }
            }
        }
    }

    fun scanTree(treeUri: Uri) {
        currentJob?.cancel()
        currentJob = scope.launch {
            mutex.withLock {
                runCatching {
                    _state.value = ScanState.Running(0, 0)
                    val items = safScanner.scan(treeUri) { count ->
                        _state.value = ScanState.Running(count, count)
                    }
                    // 空目录也要替换：否则用户移走文件后，曲库里仍留着旧记录。
                    replace(safScanner.sourceId(treeUri), items)
                    _state.value = ScanState.Done(items.size)
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.value = ScanState.Failed(R.string.library_scan_failed)
                }
            }
        }
    }

    suspend fun importTracks(items: List<TrackEntity>): Int {
        if (items.isEmpty()) return 0
        val sourceId = items.first().sourceId
        mutex.withLock { replace(sourceId, items) }
        return items.size
    }

    private suspend fun runScanLocked(showShort: Boolean) {
        runCatching {
            _state.value = ScanState.Running(0, 0)
            val scanned = mediaStoreScanner.scan(showShort) { count ->
                _state.value = ScanState.Running(count, count)
            }
            replace("local", scanned.map { it.entity })
            _state.value = ScanState.Done(scanned.size)
            fillArtwork(scanned)
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            _state.value = ScanState.Failed(R.string.library_scan_failed)
        }
    }

    private suspend fun replace(sourceId: String, items: List<TrackEntity>) {
        val before = trackDao.observeSorted(
            androidx.sqlite.db.SimpleSQLiteQuery(
                "SELECT * FROM tracks WHERE sourceId = ?", arrayOf<Any>(sourceId)
            )
        ).first().map { it.id }
        val incoming = items.map { it.id }.toSet()
        (before - incoming).forEach { trackDao.markUnavailable(it) }
        items.chunked(500).forEach { chunk -> trackDao.upsertAll(chunk) }
    }

    private suspend fun fillArtwork(scanned: List<ScannedTrack>) {
        val seen = HashMap<Long, String>()
        scanned.forEach { item ->
            val albumId = item.albumId
            if (albumId <= 0 || seen.containsKey(albumId)) return@forEach
            seen[albumId] = item.contentUri
        }
        val keys = HashMap<Long, String>()
        seen.forEach { (albumId, uri) ->
            artworkCache.extract(albumId, uri)?.let { keys[albumId] = it }
        }
        scanned.mapNotNull { item ->
            val key = keys[item.albumId] ?: return@mapNotNull null
            trackDao.byId(item.entity.id)?.let { it.copy(artworkKey = key) }
        }.chunked(500).forEach { chunk -> trackDao.upsertAll(chunk) }
    }
}
