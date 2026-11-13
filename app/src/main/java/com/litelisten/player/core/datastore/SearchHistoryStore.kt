package com.litelisten.player.core.datastore

import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryStore @Inject constructor(
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun all(): List<String> = runCatching {
        val raw = readRaw()
        if (raw.isBlank()) emptyList() else json.decodeFromString<List<String>>(raw)
    }.getOrDefault(emptyList())

    suspend fun add(query: String) {
        val next = (listOf(query.trim()) + all()).filter { it.isNotBlank() }.distinct().take(10)
        settings.put(SettingsKeys.SEARCH_HISTORY_KEY, json.encodeToString(next))
    }

    suspend fun remove(query: String) {
        settings.put(
            SettingsKeys.SEARCH_HISTORY_KEY,
            json.encodeToString(all().filterNot { it == query }),
        )
    }

    suspend fun clear() {
        settings.put(SettingsKeys.SEARCH_HISTORY_KEY, "[]")
    }

    private suspend fun readRaw(): String =
        settings.observe(SettingsKeys.SEARCH_HISTORY_KEY, "[]").first()
}
