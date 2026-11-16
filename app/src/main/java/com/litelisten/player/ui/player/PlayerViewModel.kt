package com.litelisten.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.ai.AiIntroRepository
import com.litelisten.player.core.ai.AiIntroState
import com.litelisten.player.core.ai.AiLyricsState
import com.litelisten.player.core.player.PlaybackControllerImpl
import com.litelisten.player.core.model.PlayerUiState
import com.litelisten.player.core.model.Playlist
import com.litelisten.player.core.model.RepeatMode
import com.litelisten.player.core.model.Track
import com.litelisten.player.data.repository.PlaylistRepository
import com.litelisten.player.data.repository.TrackRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val controller: PlaybackControllerImpl,
    private val tracks: TrackRepositoryImpl,
    private val playlists: PlaylistRepository,
    private val aiIntro: AiIntroRepository,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = controller.state
    val queue: StateFlow<List<Track>> = controller.queue

    /** 队列页「载入歌单」用：不订阅具体曲目，选中后再一次性取。 */
    val playlistList: StateFlow<List<Playlist>> = playlists.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 用整个歌单替换当前队列并从头播放；回调返回实际载入的歌曲数。 */
    fun loadPlaylist(playlist: Playlist, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val items = playlists.tracks(playlist.id).first()
            if (items.isNotEmpty()) controller.play(items.first(), items)
            onDone(items.size)
        }
    }

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

    private val _aiLyricsState = MutableStateFlow<AiLyricsState>(AiLyricsState.Idle)
    val aiLyricsState: StateFlow<AiLyricsState> = _aiLyricsState.asStateFlow()

    /** 讲解当前歌词：只在用户点按钮时发一次，不主动联网。 */
    fun requestLyricsExplanation(track: Track, lyrics: String) {
        if (_aiLyricsState.value is AiLyricsState.Loading) return
        _aiLyricsState.value = AiLyricsState.Loading
        viewModelScope.launch {
            _aiLyricsState.value = aiIntro.explainLyrics(track, lyrics)
        }
    }

    fun resetLyricsExplanation() {
        _aiLyricsState.value = AiLyricsState.Idle
    }

    private val _aiLyricsSearch = MutableStateFlow<AiLyricsState>(AiLyricsState.Idle)
    val aiLyricsSearch: StateFlow<AiLyricsState> = _aiLyricsSearch.asStateFlow()

    /** 联网搜歌词：只在用户点「用 AI 联网搜索」时发一次请求，不点绝不联网。 */
    fun requestLyricsSearch(track: Track) {
        if (_aiLyricsSearch.value is AiLyricsState.Loading) return
        _aiLyricsSearch.value = AiLyricsState.Loading
        viewModelScope.launch {
            _aiLyricsSearch.value = aiIntro.searchLyrics(track)
        }
    }

    fun resetLyricsSearch() {
        _aiLyricsSearch.value = AiLyricsState.Idle
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
