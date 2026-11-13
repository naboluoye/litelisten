package com.litelisten.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.player.PlaybackControllerImpl
import com.litelisten.player.core.model.PlayerUiState
import com.litelisten.player.core.model.RepeatMode
import com.litelisten.player.core.model.Track
import com.litelisten.player.data.repository.TrackRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val controller: PlaybackControllerImpl,
    private val tracks: TrackRepositoryImpl,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = controller.state
    val queue: StateFlow<List<Track>> = controller.queue

    fun play(track: Track, context: List<Track> = listOf(track)) {
        viewModelScope.launch { controller.play(track, context) }
    }

    fun playAll(items: List<Track>) {
        if (items.isEmpty()) return
        play(items.first(), items)
    }

    fun toggle() = controller.playPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seek(positionMs: Long) = controller.seekTo(positionMs)
    fun shuffle(enabled: Boolean) = controller.setShuffle(enabled)
    fun repeat(mode: RepeatMode) = controller.setRepeat(mode)
    fun speed(value: Float) = controller.setSpeed(value)
    fun sleep(minutes: Int?) = controller.setSleepTimer(minutes)
    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)
    fun moveInQueue(from: Int, to: Int) = controller.moveInQueue(from, to)
    fun clearQueue() = controller.clearQueue()
    fun playNext(track: Track) = controller.playNext(track)
    fun addToQueue(track: Track) = controller.addToQueue(track)

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { tracks.setFavorite(track.id, !track.isFavorite) }
    }

    fun removeFromLibrary(track: Track) {
        viewModelScope.launch { tracks.removeFromLibrary(track.id) }
    }
}
