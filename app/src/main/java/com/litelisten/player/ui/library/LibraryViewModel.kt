package com.litelisten.player.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.common.FolderPaths
import com.litelisten.player.core.model.Album
import com.litelisten.player.core.model.Artist
import com.litelisten.player.core.model.Playlist
import com.litelisten.player.core.player.PlaybackControllerImpl
import com.litelisten.player.core.model.SortKey
import com.litelisten.player.core.model.SortOrder
import com.litelisten.player.core.model.Track
import com.litelisten.player.core.scan.LibraryScanner
import com.litelisten.player.data.repository.PlaylistRepository
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
    private val playlistRepository: PlaylistRepository,
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

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanState = scanner.state

    fun setSort(value: SortKey) { _sort.value = value }
    fun setOrder(value: SortOrder) { _order.value = value }
    fun setSource(value: String?) { _sourceFilter.value = value }

    fun scanLocal() = scanner.scanLocal()

    fun addFolder(uri: Uri) = scanner.scanTree(uri)

    fun rebuildIndex() = scanner.rebuild()

    /** 重置：停播、清队列、清空曲库索引与歌词，并保留歌单名字，方便重新按文件夹整理。 */
    fun resetLibrary(onDone: () -> Unit) {
        viewModelScope.launch {
            controller.stop()
            controller.clearQueue()
            scanner.resetLibrary()
            onDone()
        }
    }

    fun songsInFolder(folder: String): List<Track> =
        songs.value.filter { FolderPaths.isUnder(folder, it.path) }

    /** 新建歌单；[tracks] 为空表示建空歌单。回调拿到新歌单 id。 */
    fun createPlaylist(name: String, tracks: List<Track> = emptyList(), onDone: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch { onDone(playlistRepository.createWith(name, tracks)) }
    }

    /** 把歌曲加入已有歌单；回调返回真正新增的条数（已在歌单里的会跳过）。 */
    fun addToPlaylist(playlistId: Long, tracks: List<Track>, onDone: (Int) -> Unit) {
        if (tracks.isEmpty()) return
        viewModelScope.launch { onDone(playlistRepository.addTracks(playlistId, tracks)) }
    }

    /** 一键收藏到「我喜欢的」：省去新建与选歌单的步骤。 */
    fun quickFavorite(track: Track, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(playlistRepository.quickFavorite(listOf(track))) }
    }

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
        viewModelScope.launch {
            repository.removeFromLibrary(track.id)
            playlistRepository.pruneOrphans()
        }
    }
}
