package com.litelisten.player.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.model.Playlist
import com.litelisten.player.core.model.Track
import com.litelisten.player.core.player.PlaybackControllerImpl
import com.litelisten.player.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val controller: PlaybackControllerImpl,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val playlistId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1L

    val tracks: StateFlow<List<Track>> = playlistRepository.tracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlist: StateFlow<Playlist?> = playlistRepository.playlist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun play(track: Track, context: List<Track>) {
        viewModelScope.launch { controller.play(track, context) }
    }

    fun playAll(items: List<Track>) {
        if (items.isEmpty()) return
        play(items.first(), items)
    }

    /** 随机播放整个歌单：先开随机，再按整单入队。 */
    fun shufflePlay(items: List<Track>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            controller.setShuffle(true)
            controller.play(items.first(), items)
        }
    }

    fun addToQueue(track: Track) = controller.addToQueue(track)

    fun playNext(track: Track) = controller.playNext(track)

    fun remove(track: Track) {
        viewModelScope.launch { playlistRepository.removeTrack(playlistId, track.id) }
    }

    fun rename(name: String, onDone: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistRepository.rename(playlistId, name)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            playlistRepository.delete(playlistId)
            onDone()
        }
    }
}
