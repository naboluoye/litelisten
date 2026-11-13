package com.litelisten.player.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.R
import com.litelisten.player.core.database.TrackDao
import com.litelisten.player.core.model.DiscoveredDevice
import com.litelisten.player.core.model.Source
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.core.scan.LibraryScanner
import com.litelisten.player.core.source.discovery.LanHostScanner
import com.litelisten.player.core.source.dlna.DlnaDiscovery
import com.litelisten.player.data.repository.SourceRepositoryImpl
import com.litelisten.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val repository: SourceRepositoryImpl,
    private val discovery: DlnaDiscovery,
    private val lanHostScanner: LanHostScanner,
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
    private val _hint = MutableStateFlow<Int?>(null)
    val hint: StateFlow<Int?> = _hint.asStateFlow()
    private val _scanRange = MutableStateFlow<String?>(null)
    val scanRange: StateFlow<String?> = _scanRange.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        // 进页面即扫一次；否则用户要自己找到菜单里的「扫描」才知道有发现功能。
        discover()
    }

    fun discover() {
        viewModelScope.launch {
            if (_discovering.value) return@launch
            _discovering.value = true
            _hint.value = null
            _scanRange.value = withContext(Dispatchers.IO) { lanHostScanner.subnetLabel() }
            runCatching {
                repository.discovered().collect { _devices.value = it }
            }
            _discovering.value = false
            _hint.value = when {
                _scanRange.value == null -> R.string.sources_scan_no_wifi
                _devices.value.isEmpty() -> R.string.sources_scan_no_host
                else -> null
            }
        }
    }

    /**
     * 子网扫描出来的主机只是「这台机器上开着共享端口」，没有共享名和账号，
     * 所以带 IP 跳到添加向导，而不是假装能直接连上。
     * SSDP 发现的 DLNA 设备自带 controlUrl，可以像以前一样直接保存并打开。
     */
    fun addDevice(device: DiscoveredDevice, onNavigate: (String) -> Unit) {
        if (device.id.startsWith(LAN_PREFIX) || device.id.startsWith(JF_PREFIX)) {
            val route = when (device.type) {
                SourceType.SMB -> Routes.addSmb(device.address, device.port ?: 445)
                SourceType.WEBDAV -> Routes.addWebdav(device.address, device.port ?: 80)
                SourceType.JELLYFIN -> Routes.addJellyfin(device.address, device.port ?: 8096)
                else -> return
            }
            onNavigate(route)
            return
        }
        viewModelScope.launch {
            val result = repository.add(discovery.configFor(device), null)
            result.onSuccess { onNavigate(Routes.browser(it.id)) }
        }
    }

    fun remove(sourceId: String) {
        viewModelScope.launch { repository.remove(sourceId) }
    }

    fun scanLocal() = scanner.scanLocal()

    fun consumeMessage() { _message.value = null }

    private companion object {
        const val LAN_PREFIX = "lan-"
        const val JF_PREFIX = "jf-"
    }
}
