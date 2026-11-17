package com.litelisten.player.core.scan

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.litelisten.player.R
import com.litelisten.player.core.database.LyricsDao
import com.litelisten.player.core.database.LyricsEntity
import com.litelisten.player.core.database.TrackDao
import com.litelisten.player.core.database.TrackEntity
import com.litelisten.player.core.common.DurationFilter
import com.litelisten.player.core.datastore.SettingsKeys
import com.litelisten.player.core.datastore.SettingsRepository
import com.litelisten.player.core.metadata.TagParseUtil
import com.litelisten.player.data.repository.PlaylistRepository
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
    private val lyricsDao: LyricsDao,
    private val mediaStoreScanner: MediaStoreScanner,
    private val safScanner: SafScanner,
    private val artworkCache: ArtworkCache,
    private val settings: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
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
    /**
     * 重建索引：全量重扫后增量替换。
     * 关键：不能物理 deleteAll——那会让歌单里只存 id 的条目变成悬空引用。
     * replace() 对消失的曲目用 markUnavailable 软删除（保留行与 id），
     * 再加上歌单条目的快照，双重保证歌单里的歌不凭空消失。
     */
    fun rebuild() {
        currentJob?.cancel()
        currentJob = scope.launch {
            val showShort = settings.observe(SettingsKeys.SHOW_SHORT_AUDIO_KEY, false).first()
            mutex.withLock {
                _state.value = ScanState.Running(0, 0)
                runCatching {
                    val (minMs, maxMs) = durationRange()
                    val scanned = mediaStoreScanner.scan(showShort, { count ->
                        _state.value = ScanState.Running(count, count)
                    }, minMs, maxMs)
                    replace("local", scanned.map { it.entity })
                    _state.value = ScanState.Done(scanned.size)
                    fillArtwork(scanned)
                    fillTags(scanned.map { it.entity })
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
                    val (minMs, maxMs) = durationRange()
                    val items = safScanner.scan(treeUri, { count ->
                        _state.value = ScanState.Running(count, count)
                    }, minMs, maxMs)
                    // 空目录也要替换：否则用户移走文件后，曲库里仍留着旧记录。
                    replace(safScanner.sourceId(treeUri), items)
                    _state.value = ScanState.Done(items.size)
                    // SAF 来源同样要解析内嵌歌词 / ReplayGain（以前完全漏掉）。
                    fillTags(items)
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

    /**
     * 清空曲库索引（「重置」）：删除全部曲目、内嵌歌词、手动歌词和歌单里的歌曲条目，
     * 保留歌单名字与服务器配置。只动 App 自己的数据库，绝不碰用户文件。
     */
    suspend fun resetLibrary() {
        currentJob?.cancel()
        mutex.withLock {
            runCatching {
                trackDao.deleteAll()
                lyricsDao.deleteOrphans()
                lyricsDao.deleteManualOrphans()
                playlistRepository.clearAllEntries()
                _state.value = ScanState.Idle
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.value = ScanState.Failed(R.string.library_reset_failed)
            }
        }
    }

    /** 从设置读取时长上下限（毫秒）。MIN 默认 30 秒以延续旧版「过滤短音频」行为，MAX 默认不限。 */
    private suspend fun durationRange(): Pair<Long, Long> {
        val minSec = settings.observe(SettingsKeys.MIN_DURATION_SEC_KEY, 30).first()
        val maxSec = settings.observe(SettingsKeys.MAX_DURATION_SEC_KEY, 0).first()
        return DurationFilter.secondsToMs(minSec) to DurationFilter.secondsToMs(maxSec)
    }

    private suspend fun runScanLocked(showShort: Boolean) {
        runCatching {
            _state.value = ScanState.Running(0, 0)
            val (minMs, maxMs) = durationRange()
            val scanned = mediaStoreScanner.scan(showShort, { count ->
                _state.value = ScanState.Running(count, count)
            }, minMs, maxMs)
            replace("local", scanned.map { it.entity })
            _state.value = ScanState.Done(scanned.size)
            fillArtwork(scanned)
            fillTags(scanned.map { it.entity })
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
        // 直接用扫描得到的 entity 回填，不再逐条 byId 查库（原来是每首一次 DB 往返）。
        scanned.mapNotNull { item ->
            val key = keys[item.albumId] ?: return@mapNotNull null
            item.entity.copy(artworkKey = key)
        }.chunked(500).forEach { chunk -> trackDao.upsertAll(chunk) }
    }

    /**
     * 低优先级补齐 ReplayGain 与内嵌歌词。
     *
     * 关键修复：一律通过 content uri 读取文件头，不再用 File(path)。
     * Android 10+ 的 MediaStore 只给 RELATIVE_PATH（相对目录，不以 / 开头，也不是完整文件路径），
     * SAF 给的是 content uri 且 path 是相对所选目录——两种都不是可 File 打开的绝对路径。
     * 旧实现 `path.startsWith("/")` 把所有 Q+ 曲目都挡掉，导致 lyrics 表从未写入。
     * 用 uri + ContentResolver 则本地 / SAF 都能稳定读取，且有界，不触碰用户文件。
     * 远程来源（Jellyfin/SMB/WebDAV/DLNA）没有本地 uri，跳过（其歌词走在线/同目录路径）。
     */
    private suspend fun fillTags(entities: List<TrackEntity>) {
        val updated = ArrayList<TrackEntity>()
        val lyricsRows = ArrayList<LyricsEntity>()
        entities.forEach { entity ->
            val format = tagFormat(entity) ?: return@forEach
            val cap = if (format == "flac") FLAC_HEAD_CAP else ID3_HEAD_CAP
            val head = readHead(entity.uri, cap) ?: return@forEach
            val tags = TagParseUtil.parse(head, format)
            if (!tags.hasTags) return@forEach
            var row = entity
            tags.replayGainDb?.let { row = row.copy(replayGainDb = it) }
            updated.add(row)
            val text = tags.lyrics?.trim()?.take(LYRICS_MAX_CHARS)
            if (!text.isNullOrEmpty()) {
                lyricsRows.add(LyricsEntity(row.id, TagParseUtil.looksSynced(text), text))
            }
        }
        updated.chunked(500).forEach { chunk -> trackDao.upsertAll(chunk) }
        lyricsDao.upsertAll(lyricsRows)
        lyricsDao.deleteOrphans()
    }

    /** 只处理可本地读取的容器；扩展名优先，MIME 兜底。 */
    private fun tagFormat(entity: TrackEntity): String? {
        val ext = entity.path.substringAfterLast('.', "").lowercase()
        if (ext == "flac" || ext == "mp3") return ext
        return when {
            entity.mimeType?.contains("flac", ignoreCase = true) == true -> "flac"
            entity.mimeType?.contains("mpeg", ignoreCase = true) == true -> "mp3"
            else -> null
        }
    }

    /** 通过 content uri 有界读取文件头；远程 http uri 一律跳过。 */
    private fun readHead(uriString: String, cap: Int): ByteArray? {
        if (!uriString.startsWith("content://")) return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(cap)
                var total = 0
                while (total < cap) {
                    val n = input.read(buffer, total, cap - total)
                    if (n <= 0) break
                    total += n
                }
                if (total > 0) buffer.copyOf(total) else null
            }
        }.getOrNull()
    }

    private companion object {
        const val LYRICS_MAX_CHARS = 65536
        const val FLAC_HEAD_CAP = 1 shl 20
        const val ID3_HEAD_CAP = 4 shl 20
    }
}
