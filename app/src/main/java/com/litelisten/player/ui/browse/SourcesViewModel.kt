package com.litelisten.player.ui.browse

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.R
import com.litelisten.player.core.common.UiMessage
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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

    // 不再进页面自动扫描内网：只有用户手动点「扫描局域网设备」或「刷新」时才 discover()。
    // 自动扫描会在 Wi-Fi 无互联网 + 开着蜂窝时把探测包发到蜂窝网络，既慢又会误报。

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
                .onFailure { error ->
                    // 以前失败完全静默：用户点「连接」没反应，以为卡死退不出去。
                    // 现在把真实原因写到 message，由页面以明确提示显示。
                    _message.value = if (error is com.litelisten.player.core.model.SourceError) {
                        context.getString(UiMessage.of(error), UiMessage.argOf(error))
                    } else {
                        context.getString(R.string.error_generic)
                    }
                }
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
