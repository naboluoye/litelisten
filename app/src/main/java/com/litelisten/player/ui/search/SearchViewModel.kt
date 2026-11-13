package com.litelisten.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.datastore.SearchHistoryStore
import com.litelisten.player.core.model.PlaybackController
import com.litelisten.player.core.model.SearchResult
import com.litelisten.player.core.model.Track
import com.litelisten.player.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    search: SearchRepository,
    private val history: SearchHistoryStore,
    val controller: PlaybackController,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val result: StateFlow<SearchResult> = _query
        .debounce(250L)
        .flatMapLatest { search.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResult())

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

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
}
