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

    /** 按歌词正文搜索，返回命中的曲目 id（内嵌歌词 + 手动歌词两处都查）。 */
    @Query(
        "SELECT trackId FROM lyrics WHERE content LIKE '%' || :q || '%' " +
            "UNION SELECT trackId FROM manual_lyrics WHERE content LIKE '%' || :q || '%'"
    )
    suspend fun searchByContent(q: String): List<String>
}
