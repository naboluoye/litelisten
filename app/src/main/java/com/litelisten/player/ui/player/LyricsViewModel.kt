package com.litelisten.player.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.datastore.SettingsKeys
import com.litelisten.player.core.datastore.SettingsRepository
import com.litelisten.player.core.model.Track
import com.litelisten.player.core.player.LoadedLyrics
import com.litelisten.player.core.player.LyricLine
import com.litelisten.player.core.player.LyricsImportResult
import com.litelisten.player.core.player.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val manual: StateFlow<Map<String, List<LyricLine>>> = repository.manual

    /** 歌词整体偏移（毫秒，正数=歌词往后挪）。跟唱时用来对齐口型。 */
    val offsetMs: Flow<Int> = settings.observe(SettingsKeys.LYRICS_OFFSET_MS_KEY, 0)

    val singAlong: Flow<Boolean> = settings.observe(SettingsKeys.LYRICS_SING_ALONG_KEY, false)

    val loopLine: Flow<Boolean> = settings.observe(SettingsKeys.LYRICS_LOOP_LINE_KEY, true)

    suspend fun load(track: Track?): LoadedLyrics = repository.loadAuto(track)

    /** 本地歌词全文搜索（离线），回调命中曲目。 */
    fun searchLocal(query: String, onResult: (List<Track>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = repository.searchLocal(query)
            withContext(Dispatchers.Main) { onResult(tracks) }
        }
    }

    fun attach(trackId: String, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repository.attachFile(trackId, uri)
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    /** 查询歌曲所在目录的文档 uri，供系统文件选择器定位；无结果时回调 null。 */
    fun initialFolderUri(track: Track?, onResult: (Uri?) -> Unit) {
        if (track == null) {
            onResult(null)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val uri = repository.initialFolderUri(track)
            withContext(Dispatchers.Main) { onResult(uri) }
        }
    }

    fun attachText(trackId: String, text: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repository.attachText(trackId, text)
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    fun removeManual(trackId: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.removeManual(trackId) }
    }

    fun importFolder(uri: Uri, onResult: (LyricsImportResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.importFolder(uri)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /** 按 100ms 一档微调，范围限制在 ±5 秒，避免误触后歌词彻底对不上。 */
    fun nudgeOffset(deltaMs: Int) {
        viewModelScope.launch {
            val current = offsetMs.first()
            settings.put(
                SettingsKeys.LYRICS_OFFSET_MS_KEY,
                (current + deltaMs).coerceIn(-5_000, 5_000),
            )
        }
    }

    fun resetOffset() {
        viewModelScope.launch { settings.put(SettingsKeys.LYRICS_OFFSET_MS_KEY, 0) }
    }

    fun setSingAlong(enabled: Boolean) {
        viewModelScope.launch { settings.put(SettingsKeys.LYRICS_SING_ALONG_KEY, enabled) }
    }

    fun setLoopLine(enabled: Boolean) {
        viewModelScope.launch { settings.put(SettingsKeys.LYRICS_LOOP_LINE_KEY, enabled) }
    }
}
