package org.tubepipe.tv.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tubepipe.core.database.HistoryDao
import org.tubepipe.core.database.PlaylistDao
import org.tubepipe.core.database.PlaylistEntity
import org.tubepipe.core.database.SavedSearchEntity
import org.tubepipe.core.database.SearchDao
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.TimeFormatting
import org.tubepipe.tv.common.toVideoItem

data class HistoryEntry(val video: VideoItem, val watchedFraction: Float)

data class LibraryState(
    val history: List<HistoryEntry> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val savedSearches: List<SavedSearchEntity> = emptyList(),
    val openedPlaylistId: Long? = null,
    val openedPlaylistVideos: List<VideoItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val history: HistoryDao,
    private val playlists: PlaylistDao,
    private val searches: SearchDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val openedPlaylistId = MutableStateFlow<Long?>(null)

    private val historyEntries = history.observeRecent(HISTORY_LIMIT).map { rows ->
        rows.map { row ->
            HistoryEntry(
                video = row.toVideoItem(),
                watchedFraction = TimeFormatting.watchedFraction(row.positionMillis, row.durationSeconds),
            )
        }
    }

    private val openedPlaylistVideos = openedPlaylistId.flatMapLatest { playlistId ->
        if (playlistId == null) {
            flowOf(emptyList())
        } else {
            playlists.observeItems(playlistId).map { items -> items.map { it.toVideoItem() } }
        }
    }

    val state: StateFlow<LibraryState> = combine(
        historyEntries,
        playlists.observePlaylists(),
        searches.observeSavedSearches(),
        openedPlaylistId,
        openedPlaylistVideos,
    ) { historyEntries, playlistRows, savedSearches, openedId, openedVideos ->
        LibraryState(
            history = historyEntries,
            // Liked is created first, so id order puts it on top; sorting keeps that explicit.
            playlists = playlistRows.sortedBy { it.id },
            savedSearches = savedSearches,
            openedPlaylistId = openedId,
            openedPlaylistVideos = openedVideos,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LibraryState())

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    fun createPlaylist(name: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "A playlist needs a name" }
        viewModelScope.launch { playlists.createPlaylist(trimmedName, clock()) }
    }

    fun deletePlaylist(id: Long) {
        // Liked is built in; the screen never offers Delete for it, and this guard backs that up.
        if (id == PlaylistEntity.LIKED_ID) return
        viewModelScope.launch {
            if (openedPlaylistId.value == id) openedPlaylistId.value = null
            playlists.deletePlaylist(id)
        }
    }

    fun openPlaylist(id: Long) {
        openedPlaylistId.value = id
    }

    fun closePlaylist() {
        openedPlaylistId.value = null
    }

    fun pinSavedSearch(query: String) {
        viewModelScope.launch { searches.pin(query) }
    }

    fun unpinSavedSearch() {
        viewModelScope.launch { searches.unpinAll() }
    }

    fun deleteSavedSearch(query: String) {
        viewModelScope.launch { searches.deleteSavedSearch(query) }
    }

    private companion object {
        const val HISTORY_LIMIT = 100
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
