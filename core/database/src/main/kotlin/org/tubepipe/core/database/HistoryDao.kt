package org.tubepipe.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY lastWatchedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query(
        "UPDATE history SET positionMillis = :positionMillis, lastWatchedAtMillis = :watchedAtMillis WHERE url = :url",
    )
    suspend fun savePosition(url: String, positionMillis: Long, watchedAtMillis: Long)

    @Query("SELECT positionMillis FROM history WHERE url = :url")
    suspend fun positionOf(url: String): Long?

    @Query("DELETE FROM history")
    suspend fun clear()
}
