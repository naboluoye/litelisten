package com.litelisten.player.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** 《05 契约》§10 全部键名；P0 只读写 theme_mode / onboarding_done，其余先冻结。 */
object SettingsKeys {
    const val THEME_MODE = "theme_mode"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val LIST_DENSITY = "list_density"
    const val HEADSET_UNPLUG_PAUSE = "headset_unplug_pause"
    const val FADE_ENABLED = "fade_enabled"
    const val RESUME_ON_START = "resume_on_start"
    const val AUTO_PLAY_ON_START = "auto_play_on_start"
    const val SKIP_ON_ERROR = "skip_on_error"
    const val PLAYBACK_SPEED = "playback_speed"
    const val EQ_ENABLED = "eq_enabled"
    const val EQ_PRESET = "eq_preset"
    const val BASS_BOOST = "bass_boost"
    const val CACHE_LIMIT_MB = "cache_limit_mb"
    const val BUFFER_SECONDS = "buffer_seconds"
    const val CONNECT_TIMEOUT_S = "connect_timeout_s"
    const val WIFI_ONLY = "wifi_only"
    const val SHOW_SHORT_AUDIO = "show_short_audio"
    const val SEARCH_HISTORY = "search_history"
    const val QUEUE_SNAPSHOT = "queue_snapshot"
    const val LAST_SCAN_AT = "last_scan_at"
    const val ONBOARDING_DONE = "onboarding_done"

    val THEME_MODE_KEY = stringPreferencesKey(THEME_MODE)
    val DYNAMIC_COLOR_KEY = booleanPreferencesKey(DYNAMIC_COLOR)
    val LIST_DENSITY_KEY = stringPreferencesKey(LIST_DENSITY)
    val HEADSET_UNPLUG_PAUSE_KEY = booleanPreferencesKey(HEADSET_UNPLUG_PAUSE)
    val FADE_ENABLED_KEY = booleanPreferencesKey(FADE_ENABLED)
    val RESUME_ON_START_KEY = booleanPreferencesKey(RESUME_ON_START)
    val AUTO_PLAY_ON_START_KEY = booleanPreferencesKey(AUTO_PLAY_ON_START)
    val SKIP_ON_ERROR_KEY = booleanPreferencesKey(SKIP_ON_ERROR)
    val PLAYBACK_SPEED_KEY = floatPreferencesKey(PLAYBACK_SPEED)
    val EQ_ENABLED_KEY = booleanPreferencesKey(EQ_ENABLED)
    val EQ_PRESET_KEY = stringPreferencesKey(EQ_PRESET)
    val BASS_BOOST_KEY = intPreferencesKey(BASS_BOOST)
    val CACHE_LIMIT_MB_KEY = intPreferencesKey(CACHE_LIMIT_MB)
    val BUFFER_SECONDS_KEY = intPreferencesKey(BUFFER_SECONDS)
    val CONNECT_TIMEOUT_S_KEY = intPreferencesKey(CONNECT_TIMEOUT_S)
    val WIFI_ONLY_KEY = booleanPreferencesKey(WIFI_ONLY)
    val SHOW_SHORT_AUDIO_KEY = booleanPreferencesKey(SHOW_SHORT_AUDIO)
    val SEARCH_HISTORY_KEY = stringPreferencesKey(SEARCH_HISTORY)
    val QUEUE_SNAPSHOT_KEY = stringPreferencesKey(QUEUE_SNAPSHOT)
    val LAST_SCAN_AT_KEY = longPreferencesKey(LAST_SCAN_AT)
    val ONBOARDING_DONE_KEY = booleanPreferencesKey(ONBOARDING_DONE)
}
