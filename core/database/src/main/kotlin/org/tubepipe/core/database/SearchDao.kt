package org.tubepipe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SearchDao {
    @Upsert
    protected abstract suspend fun upsertHistory(entry: SearchHistoryEntity)

    suspend fun recordSearch(query: String, searchedAtMillis: Long) {
        upsertHistory(SearchHistoryEntity(query = query, searchedAtMillis = searchedAtMillis))
    }

    @Query("SELECT * FROM search_history ORDER BY searchedAtMillis DESC LIMIT :limit")
    abstract fun observeRecentSearches(limit: Int): Flow<List<SearchHistoryEntity>>

    // IGNORE so saving an already saved (possibly pinned) search does not reset its pin.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSavedSearch(savedSearch: SavedSearchEntity)

    suspend fun saveSearch(query: String) {
        insertSavedSearch(SavedSearchEntity(query = query, pinned = false))
    }

    @Query("SELECT * FROM saved_search ORDER BY `query` COLLATE NOCASE ASC")
    abstract fun observeSavedSearches(): Flow<List<SavedSearchEntity>>

    @Query("SELECT * FROM saved_search WHERE pinned = 1 LIMIT 1")
    abstract fun observePinned(): Flow<SavedSearchEntity?>

    @Query("UPDATE saved_search SET pinned = 0")
    abstract suspend fun unpinAll()

    @Query("UPDATE saved_search SET pinned = 1 WHERE `query` = :query")
    protected abstract suspend fun markPinned(query: String)

    // One transaction so observers never see zero or two pinned searches.
    @Transaction
    open suspend fun pin(query: String) {
        unpinAll()
        markPinned(query)
    }

    @Query("DELETE FROM saved_search WHERE `query` = :query")
    abstract suspend fun deleteSavedSearch(query: String)

    @Query("DELETE FROM search_history")
    abstract suspend fun clearSearchHistory()
}
