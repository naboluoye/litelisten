package com.litelisten.player.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.database.TrackDao
import com.litelisten.player.core.model.DiscoveredDevice
import com.litelisten.player.core.model.Source
import com.litelisten.player.core.scan.LibraryScanner
import com.litelisten.player.core.source.dlna.DlnaDiscovery
import com.litelisten.player.data.repository.SourceRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val repository: SourceRepositoryImpl,
    private val discovery: DlnaDiscovery,
    private val scanner: LibraryScanner,
    trackDao: TrackDao,
) : ViewModel() {
    val sources: StateFlow<List<Source>> = repository.sources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val trackCount: StateFlow<Int> = trackDao.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val scanState = scanner.state

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()
    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun discover() {
        viewModelScope.launch {
            _discovering.value = true
            runCatching { repository.discovered().collect { _devices.value = it } }
            _discovering.value = false
        }
    }

    fun addDevice(device: DiscoveredDevice, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.add(discovery.configFor(device), null)
            result.onSuccess { onDone(it.id) }
        }
    }

    fun remove(sourceId: String) {
        viewModelScope.launch { repository.remove(sourceId) }
    }

    fun scanLocal() = scanner.scanLocal()

    fun consumeMessage() { _message.value = null }
}
