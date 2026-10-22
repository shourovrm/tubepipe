package org.tubepipe.tv.trending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.TrendingCatalog
import org.tubepipe.core.service.TrendingKind
import org.tubepipe.core.service.VideoItem
import org.tubepipe.tv.data.AppSettings

data class TrendingState(
    val tabs: List<TrendingKind> = emptyList(),
    val selected: TrendingKind? = null,
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
)

class TrendingViewModel(private val content: ContentRepository, settings: Flow<AppSettings>) : ViewModel() {
    private val mutableState = MutableStateFlow(TrendingState())
    val state: StateFlow<TrendingState> = mutableState.asStateFlow()

    // One entry per tab already fetched in this country, so switching back to a tab is instant.
    private val videosByTab = mutableMapOf<TrendingKind, List<VideoItem>>()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings
                .map { it.contentCountry }
                .distinctUntilChanged()
                .collect { country -> showCountry(country) }
        }
    }

    fun select(kind: TrendingKind) {
        if (kind == mutableState.value.selected) {
            return
        }
        showTab(kind)
    }

    fun retry() {
        val kind = mutableState.value.selected ?: return
        showTab(kind)
    }

    /** The tab list depends on the country, and cached videos belong to the old country. */
    private fun showCountry(country: String) {
        val tabs = TrendingCatalog.kindsFor(country)
        videosByTab.clear()
        mutableState.value = TrendingState(tabs = tabs)
        showTab(tabs.first())
    }

    private fun showTab(kind: TrendingKind) {
        loadJob?.cancel()
        val cached = videosByTab[kind]
        if (cached != null) {
            mutableState.update { it.copy(selected = kind, videos = cached, isLoading = false, hasError = false) }
            return
        }
        mutableState.update { it.copy(selected = kind, videos = emptyList(), isLoading = true, hasError = false) }
        loadJob = viewModelScope.launch { load(kind) }
    }

    /** The repository reports every YouTube failure as IOException; it is logged and shown as the error state. */
    private suspend fun load(kind: TrendingKind) {
        try {
            val videos = content.trending(kind)
            videosByTab[kind] = videos
            mutableState.update { it.copy(videos = videos, isLoading = false, hasError = false) }
        } catch (failure: IOException) {
            showFailure(kind, failure)
        }
    }

    private fun showFailure(kind: TrendingKind, failure: IOException) {
        logger.log(Level.WARNING, "Trending tab $kind failed to load", failure)
        mutableState.update { it.copy(videos = emptyList(), isLoading = false, hasError = true) }
    }

    private companion object {
        val logger: Logger = Logger.getLogger(TrendingViewModel::class.java.name)
    }
}
