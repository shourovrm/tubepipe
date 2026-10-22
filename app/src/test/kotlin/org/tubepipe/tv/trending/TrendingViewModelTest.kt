package org.tubepipe.tv.trending

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tubepipe.core.service.TrendingKind
import org.tubepipe.tv.testing.FakeContentRepository
import org.tubepipe.tv.testing.MainDispatcherRule
import org.tubepipe.tv.testing.testSettings
import org.tubepipe.tv.testing.videos

class TrendingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val content = FakeContentRepository().apply {
        trendingVideos[TrendingKind.Music] = videos("music", 12)
        trendingVideos[TrendingKind.MoviesAndShows] = videos("movie", 12)
        trendingVideos[TrendingKind.Gaming] = videos("game", 12)
        trendingVideos[TrendingKind.News] = videos("news", 12)
    }

    @Test
    fun tabsFollowTheContentCountryAndTheFirstIsLoaded() = runTest(timeout = 30.seconds) {
        val viewModel = TrendingViewModel(content, testSettings(contentCountry = "BD"))

        val state = viewModel.state.first { !it.isLoading && it.videos.isNotEmpty() }

        assertEquals(listOf(TrendingKind.MoviesAndShows, TrendingKind.Gaming, TrendingKind.News), state.tabs)
        assertEquals(TrendingKind.MoviesAndShows, state.selected)
        assertEquals(videos("movie", 12), state.videos)
    }

    @Test
    fun selectingATabLoadsItOnceAndCachesIt() = runTest(timeout = 30.seconds) {
        val viewModel = TrendingViewModel(content, testSettings(contentCountry = "US"))
        viewModel.state.first { !it.isLoading && it.videos.isNotEmpty() }

        viewModel.select(TrendingKind.Gaming)
        assertEquals(
            videos("game", 12),
            viewModel.state.first {
                it.selected == TrendingKind.Gaming && !it.isLoading
            }.videos,
        )

        viewModel.select(TrendingKind.Music)
        viewModel.state.first { it.selected == TrendingKind.Music && !it.isLoading }

        assertEquals(listOf(TrendingKind.Music, TrendingKind.Gaming), content.trendingRequests)
    }

    @Test
    fun failureShowsAnErrorAndRetryRecovers() = runTest(timeout = 30.seconds) {
        content.failures += "trending:Music"
        val viewModel = TrendingViewModel(content, testSettings(contentCountry = "US"))

        assertTrue(viewModel.state.first { !it.isLoading }.hasError)

        content.failures.clear()
        viewModel.retry()
        val recovered = viewModel.state.first { !it.isLoading && it.videos.isNotEmpty() }
        assertEquals(false, recovered.hasError)
    }

    @Test
    fun changingTheCountryResetsTabsAndReloads() = runTest(timeout = 30.seconds) {
        val settings = testSettings(contentCountry = "US")
        val viewModel = TrendingViewModel(content, settings)
        viewModel.state.first { !it.isLoading && it.videos.isNotEmpty() }

        settings.value = settings.value.copy(contentCountry = "BD")

        val state = viewModel.state.first { it.tabs.size == 3 && !it.isLoading && it.videos.isNotEmpty() }
        assertEquals(TrendingKind.MoviesAndShows, state.selected)
    }
}
