package com.litelisten.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.datastore.SearchHistoryStore
import com.litelisten.player.core.model.PlaybackController
import com.litelisten.player.core.model.Playlist
import com.litelisten.player.core.model.SearchResult
import com.litelisten.player.core.model.Track
import com.litelisten.player.data.repository.PlaylistRepository
import com.litelisten.player.data.repository.SearchRepository
import com.litelisten.player.data.repository.TrackRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    search: SearchRepository,
    private val history: SearchHistoryStore,
    val controller: PlaybackController,
    private val tracksRepo: TrackRepositoryImpl,
    private val playlists: PlaylistRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val result: StateFlow<SearchResult> = _query
        .debounce(250L)
        .flatMapLatest { search.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResult())

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    // ---- 多选加入歌单 ----
    private val _selectMode = MutableStateFlow(false)
    val selectMode: StateFlow<Boolean> = _selectMode.asStateFlow()
    private val _selected = MutableStateFlow<LinkedHashMap<String, Track>>(LinkedHashMap())
    val selected: StateFlow<List<Track>> = _selected.map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlistList: StateFlow<List<Playlist>> = playlists.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _recent.value = history.all() }
    }

    fun setQuery(value: String) { _query.value = value }

    fun submit(value: String) {
        _query.value = value
        viewModelScope.launch { history.add(value); _recent.value = history.all() }
    }

    fun removeRecent(value: String) {
        viewModelScope.launch { history.remove(value); _recent.value = history.all() }
    }

    fun clearRecent() {
        viewModelScope.launch { history.clear(); _recent.value = emptyList() }
    }

    fun play(track: Track, context: List<Track>) {
        viewModelScope.launch { controller.play(track, context) }
    }

    fun toggleSelectMode(on: Boolean) {
        _selectMode.value = on
        if (!on) _selected.value = LinkedHashMap()
    }

    fun toggleTrack(track: Track) = setTracksSelected(listOf(track), !isSelected(track.id))

    fun isSelected(id: String): Boolean = _selected.value.containsKey(id)

    /** 批量选中/取消一组曲目（全选、专辑、艺术家共用）。 */
    fun setTracksSelected(items: List<Track>, select: Boolean) {
        val map = LinkedHashMap(_selected.value)
        items.forEach { if (select) map[it.id] = it else map.remove(it.id) }
        _selected.value = map
    }

    /** 全选/取消：覆盖当前搜索到的所有歌曲。 */
    fun selectAll(all: List<Track>, select: Boolean) = setTracksSelected(all, select)

    /** 勾选专辑/艺术家时，把其下所有曲目加入选择集（异步读一次库）。 */
    fun selectGroup(kind: GroupKind, name: String, select: Boolean) {
        viewModelScope.launch {
            val items = when (kind) {
                GroupKind.ALBUM -> tracksRepo.byAlbum(name).first()
                GroupKind.ARTIST -> tracksRepo.byArtist(name).first()
            }
            setTracksSelected(items, select)
        }
    }

    fun addSelectedToPlaylist(playlistId: Long, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val count = playlists.addTracks(playlistId, _selected.value.values.toList())
            toggleSelectMode(false)
            onDone(count)
        }
    }

    fun createPlaylistAndAdd(name: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val count = _selected.value.size
            playlists.createWith(name, _selected.value.values.toList())
            toggleSelectMode(false)
            onDone(count)
        }
    }

    enum class GroupKind { ALBUM, ARTIST }
}
