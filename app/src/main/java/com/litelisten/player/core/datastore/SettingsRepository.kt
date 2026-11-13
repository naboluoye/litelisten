package com.litelisten.player.core.datastore

import com.litelisten.player.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val onboardingDone: Flow<Boolean>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOnboardingDone(done: Boolean)
}
