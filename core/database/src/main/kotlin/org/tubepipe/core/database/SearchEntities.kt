package org.tubepipe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A query the user ran. The query text is the key, so repeating a search only refreshes its time. */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(@PrimaryKey val query: String, val searchedAtMillis: Long)

/** A search the user chose to keep. At most one is pinned, and the pinned one appears in the drawer. */
@Entity(tableName = "saved_search")
data class SavedSearchEntity(@PrimaryKey val query: String, val pinned: Boolean)
