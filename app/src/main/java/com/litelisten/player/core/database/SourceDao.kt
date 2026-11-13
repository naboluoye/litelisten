package com.litelisten.player.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun byId(id: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE type = :type")
    suspend fun byType(type: String): List<SourceEntity>

    @Upsert
    suspend fun upsert(entity: SourceEntity)

    @Query("UPDATE sources SET trackCount = :count, lastSyncAt = :at WHERE id = :id")
    suspend fun updateSync(id: String, count: Int, at: Long)

    @Query("UPDATE sources SET lastConnectedAt = :at WHERE id = :id")
    suspend fun updateConnected(id: String, at: Long)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)
}
