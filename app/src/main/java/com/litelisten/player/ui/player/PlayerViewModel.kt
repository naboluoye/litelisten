package com.litelisten.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.ai.AiIntroRepository
import com.litelisten.player.core.ai.AiIntroState
import com.litelisten.player.core.player.PlaybackControllerImpl
import com.litelisten.player.core.model.PlayerUiState
import com.litelisten.player.core.model.RepeatMode
import com.litelisten.player.core.model.Track
import com.litelisten.player.data.repository.TrackRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val controller: PlaybackControllerImpl,
    private val tracks: TrackRepositoryImpl,
    private val aiIntro: AiIntroRepository,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = controller.state
    val queue: StateFlow<List<Track>> = controller.queue

    private val _aiIntroState = MutableStateFlow<AiIntroState>(AiIntroState.Idle)
    val aiIntroState: StateFlow<AiIntroState> = _aiIntroState.asStateFlow()

    val aiEnabled: Boolean get() = com.litelisten.player.BuildConfig.AI_API_KEY.isNotBlank()

    fun requestAiIntro(track: Track) {
        if (_aiIntroState.value is AiIntroState.Loading) return
        _aiIntroState.value = AiIntroState.Loading
        viewModelScope.launch {
            _aiIntroState.value = aiIntro.fetchIntro(track)
        }
    }

    fun resetAiIntro() {
        _aiIntroState.value = AiIntroState.Idle
    }

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
