package org.tubepipe.tv.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tubepipe.core.database.SearchDao
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.PageCursor
import org.tubepipe.core.service.VideoItem

data class SearchState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val results: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val submittedQuery: String? = null,
    val canLoadMore: Boolean = false,
    val savedQueries: Set<String> = emptySet(),
) {
    val isCurrentSearchSaved: Boolean get() = submittedQuery != null && submittedQuery in savedQueries
}

class SearchViewModel(
    private val content: ContentRepository,
    private val searchDao: SearchDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = mutableState

    private var nextPageCursor: PageCursor? = null
    private var suggestionJob: Job? = null
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            searchDao.observeRecentSearches(RECENT_SEARCH_LIMIT).collect { entries ->
                mutableState.update { it.copy(recentSearches = entries.map { entry -> entry.query }) }
            }
        }
        viewModelScope.launch {
            searchDao.observeSavedSearches().collect { entries ->
                mutableState.update { it.copy(savedQueries = entries.map { entry -> entry.query }.toSet()) }
            }
        }
    }

    fun onQueryChange(query: String) {
        mutableState.update { it.copy(query = query) }
        suggestionJob?.cancel()
        if (query.isBlank()) {
            mutableState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionJob = viewModelScope.launch {
            // Waiting out the typing pause keeps one request per word instead of one per key press.
            delay(SUGGESTION_DELAY_MILLIS)
            val suggestions = fetchSuggestions(query)
            mutableState.update { it.copy(suggestions = suggestions) }
        }
    }

    fun submit(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return
        }
        suggestionJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()
        nextPageCursor = null
        mutableState.update {
            it.copy(
                query = trimmedQuery,
                suggestions = emptyList(),
                results = emptyList(),
                isLoading = true,
                hasError = false,
                submittedQuery = trimmedQuery,
                canLoadMore = false,
            )
        }
        searchJob = viewModelScope.launch {
            searchDao.recordSearch(trimmedQuery, searchedAtMillis = clock())
            loadFirstPage(trimmedQuery)
        }
    }

    fun retry() {
        val query = mutableState.value.submittedQuery ?: return
        submit(query)
    }

    fun loadMore() {
        val query = mutableState.value.submittedQuery ?: return
        val cursor = nextPageCursor ?: return
        if (loadMoreJob?.isActive == true || mutableState.value.isLoading) {
            return
        }
        loadMoreJob = viewModelScope.launch {
            try {
                val page = content.searchMore(query, cursor)
                nextPageCursor = page.next
                mutableState.update {
                    it.copy(results = it.results + page.items, canLoadMore = page.next != null)
                }
            } catch (failure: IOException) {
                // Keep the results already shown and the cursor, so a later scroll can try again.
                Log.w(TAG, "Loading more results for \"$query\" failed", failure)
            }
        }
    }

    fun saveCurrentSearch() {
        val query = mutableState.value.submittedQuery ?: return
        viewModelScope.launch { searchDao.saveSearch(query) }
    }

    private suspend fun loadFirstPage(query: String) {
        try {
            val page = content.search(query)
            nextPageCursor = page.next
            mutableState.update {
                it.copy(results = page.items, isLoading = false, canLoadMore = page.next != null)
            }
        } catch (failure: IOException) {
            Log.e(TAG, "Search for \"$query\" failed", failure)
            mutableState.update { it.copy(isLoading = false, hasError = true) }
        }
    }

    private suspend fun fetchSuggestions(query: String): List<String> = try {
        content.searchSuggestions(query)
    } catch (failure: IOException) {
        // Suggestions are a convenience; typing and submitting still work without them.
        Log.w(TAG, "Suggestions for \"$query\" failed", failure)
        emptyList()
    }

    companion object {
        const val SUGGESTION_DELAY_MILLIS = 300L
        private const val RECENT_SEARCH_LIMIT = 10
        private const val TAG = "SearchViewModel"
    }
}
