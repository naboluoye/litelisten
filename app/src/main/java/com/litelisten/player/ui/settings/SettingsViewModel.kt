package com.litelisten.player.ui.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.core.datastore.SettingsKeys
import com.litelisten.player.core.datastore.SettingsRepository
import com.litelisten.player.core.model.ThemeMode
import com.litelisten.player.core.player.PlaybackControllerImpl
import com.litelisten.player.core.scan.ArtworkCache
import com.litelisten.player.core.scan.LibraryScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val artworkCache: ArtworkCache,
    private val controller: PlaybackControllerImpl,
    val scanner: LibraryScanner,
) : ViewModel() {
    val prefs: StateFlow<Preferences> = settings.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyPreferences())

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val onboardingDone: StateFlow<Boolean> = settings.onboardingDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _cacheCleared = MutableStateFlow<Long?>(null)
    val cacheCleared: StateFlow<Long?> = _cacheCleared.asStateFlow()

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            settings.put(key, value)
            if (key == SettingsKeys.PLAYBACK_SPEED_KEY && value is Float) {
                controller.setSpeed(value)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setOnboardingDone(done: Boolean) {
        viewModelScope.launch { settings.setOnboardingDone(done) }
    }

    fun scanNow() = scanner.scanLocal()

    fun rebuildIndex() {
        scanner.rebuild()
        _cacheCleared.value = -1L
    }

    fun clearArtworkCache() {
        _cacheCleared.value = artworkCache.clear()
    }

    fun consumeCacheCleared() { _cacheCleared.value = null }
}
