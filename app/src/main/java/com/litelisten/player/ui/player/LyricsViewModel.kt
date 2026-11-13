package com.litelisten.player.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.model.Track
import com.litelisten.player.core.player.LyricLine
import com.litelisten.player.core.player.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
) : ViewModel() {
    val manual: StateFlow<Map<String, List<LyricLine>>> = repository.manual

    suspend fun auto(track: Track?): List<LyricLine> = repository.loadAuto(track)

    fun attach(trackId: String, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val lines = repository.readFromUri(uri)
            if (lines.isNotEmpty()) repository.setManual(trackId, lines)
            withContext(Dispatchers.Main) { onResult(lines.isNotEmpty()) }
        }
    }
}
