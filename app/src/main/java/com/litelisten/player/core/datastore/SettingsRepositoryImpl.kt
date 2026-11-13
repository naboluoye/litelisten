package com.litelisten.player.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.litelisten.player.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val all: Flow<Preferences> = dataStore.data

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull {
            it.name.equals(prefs[SettingsKeys.THEME_MODE_KEY] ?: "SYSTEM", ignoreCase = true)
        } ?: ThemeMode.SYSTEM
    }

    override val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SettingsKeys.ONBOARDING_DONE_KEY] ?: false
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[SettingsKeys.THEME_MODE_KEY] = mode.name.lowercase() }
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[SettingsKeys.ONBOARDING_DONE_KEY] = done }
    }

    override fun <T> observe(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data.map { prefs -> prefs[key] ?: default }

    override suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}
