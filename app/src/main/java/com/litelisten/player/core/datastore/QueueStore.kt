package com.litelisten.player.core.datastore

import com.litelisten.player.core.model.RepeatMode
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class QueueSnapshot(
    val ids: List<String> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: String = RepeatMode.ALL.name,
    val speed: Float = 1f,
)

@Singleton
class QueueStore @Inject constructor(
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(snapshot: QueueSnapshot) {
        settings.put(SettingsKeys.QUEUE_SNAPSHOT_KEY, json.encodeToString(snapshot))
    }

    suspend fun restore(): QueueSnapshot? = runCatching {
        val raw = settings.observe(SettingsKeys.QUEUE_SNAPSHOT_KEY, "").first()
        if (raw.isBlank()) null else json.decodeFromString<QueueSnapshot>(raw)
    }.getOrNull()

    suspend fun clear() {
        settings.put(SettingsKeys.QUEUE_SNAPSHOT_KEY, "")
    }
}
