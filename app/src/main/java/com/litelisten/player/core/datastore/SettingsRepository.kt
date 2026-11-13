package com.litelisten.player.core.datastore

import androidx.datastore.preferences.core.Preferences
import com.litelisten.player.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val all: Flow<Preferences>
    val themeMode: Flow<ThemeMode>
    val onboardingDone: Flow<Boolean>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOnboardingDone(done: Boolean)

    fun <T> observe(key: Preferences.Key<T>, default: T): Flow<T>
    suspend fun <T> put(key: Preferences.Key<T>, value: T)
}
