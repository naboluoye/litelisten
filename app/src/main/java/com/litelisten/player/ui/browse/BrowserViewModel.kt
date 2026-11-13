package com.litelisten.player.ui.browse

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.R
import com.litelisten.player.core.common.MimeTypes
import com.litelisten.player.core.common.UiMessage
import com.litelisten.player.core.model.PlaybackController
import com.litelisten.player.core.model.RemoteEntry
import com.litelisten.player.core.model.SourceError
import com.litelisten.player.data.repository.SourceRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowserUiState(
    val loading: Boolean = false,
    val entries: List<RemoteEntry> = emptyList(),
    val path: String = "",
    @StringRes val messageRes: Int? = null,
    val messageArg: String = "",
    val scanResult: Int? = null,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: SourceRepositoryImpl,
    val controller: PlaybackController,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val sourceId: String = savedState.get<String>("sourceId").orEmpty()
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    init {
        load(savedState.get<String>("path").orEmpty())
    }

    fun load(path: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, path = path, messageRes = null)
            repository.browse(sourceId, path).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, entries = it) },
                onFailure = {
                    _state.value = _state.value.copy(
                        loading = false,
                        messageRes = if (it is SourceError) UiMessage.of(it) else R.string.error_generic,
                        messageArg = "",
                    )
                },
            )
        }
    }

    fun play(entry: RemoteEntry) {
        viewModelScope.launch {
            val playable = _state.value.entries.filter {
                !it.isDirectory && MimeTypes.isSupported(it.name)
            }
            val tracks = repository.playableTracks(sourceId, playable)
            val target = tracks.firstOrNull { it.path == entry.path } ?: return@launch
            controller.play(target, tracks)
        }
    }

    fun addToLibrary() {
        viewModelScope.launch {
            repository.scanIntoLibrary(sourceId, _state.value.path).fold(
                onSuccess = { _state.value = _state.value.copy(scanResult = it) },
                onFailure = {
                    _state.value = _state.value.copy(
                        messageRes = if (it is SourceError) UiMessage.of(it) else R.string.error_generic,
                    )
                },
            )
        }
    }

    fun consumeScanResult() { _state.value = _state.value.copy(scanResult = null) }
}
