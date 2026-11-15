package com.litelisten.player.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun byTrackId(trackId: String): LyricsEntity?

    @Upsert
    suspend fun upsertAll(items: List<LyricsEntity>)

    @Query("DELETE FROM lyrics WHERE trackId NOT IN (SELECT id FROM tracks)")
    suspend fun deleteOrphans()

    @Query("SELECT * FROM manual_lyrics WHERE trackId = :trackId")
    suspend fun manualByTrackId(trackId: String): ManualLyricsEntity?

    @Query("SELECT trackId FROM manual_lyrics")
    suspend fun manualTrackIds(): List<String>

    @Upsert
    suspend fun upsertManual(items: List<ManualLyricsEntity>)

    @Query("DELETE FROM manual_lyrics WHERE trackId = :trackId")
    suspend fun deleteManual(trackId: String)

    @Query("DELETE FROM manual_lyrics WHERE trackId NOT IN (SELECT id FROM tracks)")
    suspend fun deleteManualOrphans()
}
