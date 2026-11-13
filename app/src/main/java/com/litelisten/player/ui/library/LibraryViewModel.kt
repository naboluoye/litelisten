package com.litelisten.player.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.model.Album
import com.litelisten.player.core.model.Artist
import com.litelisten.player.core.player.PlaybackControllerImpl
import com.litelisten.player.core.model.SortKey
import com.litelisten.player.core.model.SortOrder
import com.litelisten.player.core.model.Track
import com.litelisten.player.core.scan.LibraryScanner
import com.litelisten.player.data.repository.TrackRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: TrackRepositoryImpl,
    val controller: PlaybackControllerImpl,
    val scanner: LibraryScanner,
) : ViewModel() {
    private val _sort = MutableStateFlow(SortKey.TITLE)
    val sort: StateFlow<SortKey> = _sort.asStateFlow()
    private val _order = MutableStateFlow(SortOrder.ASC)
    val order: StateFlow<SortOrder> = _order.asStateFlow()
    private val _sourceFilter = MutableStateFlow<String?>(null)
    val sourceFilter: StateFlow<String?> = _sourceFilter.asStateFlow()

    val songs: StateFlow<List<Track>> = combine(_sort, _order, _sourceFilter) { s, o, f ->
        Triple(s, o, f)
    }.flatMapLatest { (s, o, f) -> repository.allTracks(s, o, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = repository.albums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists: StateFlow<List<Artist>> = repository.artists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders: StateFlow<List<String>> = repository.folders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val count: StateFlow<Int> = repository.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val scanState = scanner.state

    fun setSort(value: SortKey) { _sort.value = value }
    fun setOrder(value: SortOrder) { _order.value = value }
    fun setSource(value: String?) { _sourceFilter.value = value }

    fun scanLocal() = scanner.scanLocal()

    fun autoScanIfEmpty() = scanner.scanLocalIfEmpty()

    fun addFolder(uri: Uri) = scanner.scanTree(uri)

    fun rebuildIndex() = scanner.rebuild()

    fun play(track: Track, context: List<Track>) {
        viewModelScope.launch { controller.play(track, context) }
    }

    fun playAll(items: List<Track>) {
        if (items.isEmpty()) return
        play(items.first(), items)
    }

    fun playNext(track: Track) = controller.playNext(track)

    fun addToQueue(track: Track) = controller.addToQueue(track)

    fun favorite(track: Track) {
        viewModelScope.launch { repository.setFavorite(track.id, !track.isFavorite) }
    }

    fun remove(track: Track) {
        viewModelScope.launch { repository.removeFromLibrary(track.id) }
    }
}
