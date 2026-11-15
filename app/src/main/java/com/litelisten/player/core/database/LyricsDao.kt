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
}
