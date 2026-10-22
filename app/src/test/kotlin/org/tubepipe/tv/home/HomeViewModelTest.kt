package org.tubepipe.tv.home

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tubepipe.core.database.HistoryEntity
import org.tubepipe.core.database.SubscriptionEntity
import org.tubepipe.core.service.TrendingKind
import org.tubepipe.core.service.VideoItem
import org.tubepipe.tv.data.AppSettings
import org.tubepipe.tv.data.HomeSection
import org.tubepipe.tv.testing.FakeContentRepository
import org.tubepipe.tv.testing.MainDispatcherRule
import org.tubepipe.tv.testing.TestDatabaseRule
import org.tubepipe.tv.testing.testSettings
import org.tubepipe.tv.testing.video
import org.tubepipe.tv.testing.videos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val databaseRule = TestDatabaseRule()

    private val content = FakeContentRepository().apply {
        trendingVideos[TrendingKind.Music] = videos("music", 10)
        trendingVideos[TrendingKind.MoviesAndShows] = videos("movie", 10)
        feed = videos("feed", 10)
    }

    private fun historyEntry(video: VideoItem, watchedAtMillis: Long, positionMillis: Long = 60_000L) = HistoryEntity(
        url = video.url,
        title = video.title,
        channelName = video.channelName,
        thumbnailUrl = video.thumbnailUrl,
        durationSeconds = video.durationSeconds,
        lastWatchedAtMillis = watchedAtMillis,
        positionMillis = positionMillis,
    )

    private suspend fun watch(video: VideoItem, watchedAtMillis: Long, positionMillis: Long = 60_000L) {
        databaseRule.database.historyDao().upsert(historyEntry(video, watchedAtMillis, positionMillis))
        content.addDetails(video, related = videos("related-${video.url.takeLast(2)}-", 8))
        content.mixes[video.url] = videos("mix-${video.url.takeLast(2)}-", 8)
    }

    private suspend fun subscribe(channelUrl: String) {
        databaseRule.database.subscriptionDao().upsert(
            SubscriptionEntity(url = channelUrl, name = "Sub", avatarUrl = null, subscribedAtMillis = 1L),
        )
    }

    private fun viewModel(settings: AppSettings = testSettings().value) = HomeViewModel(
        content = content,
        history = databaseRule.database.historyDao(),
        subscriptions = databaseRule.database.subscriptionDao(),
        settings = MutableStateFlow(settings),
    )

    private suspend fun HomeViewModel.loaded(): HomeState = state.first { !it.isLoading }

    @Test
    fun rowsFollowTheSettingsOrderAndSkipHiddenSections() = runTest(timeout = 30.seconds) {
        watch(video("a1"), watchedAtMillis = 1)
        subscribe("https://www.youtube.com/channel/Sub")
        val settings = testSettings(hiddenHomeSections = setOf(HomeSection.Mix)).value

        val rows = viewModel(settings).loaded().rows

        assertEquals(
            listOf(
                HomeSection.ContinueWatching,
                HomeSection.BecauseYouWatched,
                HomeSection.NewFromSubscriptions,
                HomeSection.TrendingMusic,
                HomeSection.TrendingMovies,
            ),
            rows.map { it.section },
        )
        assertEquals(listOf("https://www.youtube.com/channel/Sub"), content.feedRequest)
    }

    @Test
    fun sectionsWithoutDataAreLeftOut() = runTest(timeout = 30.seconds) {
        val rows = viewModel().loaded().rows

        assertEquals(listOf(HomeSection.TrendingMusic, HomeSection.TrendingMovies), rows.map { it.section })
        assertEquals(null, content.feedRequest)
    }

    @Test
    fun becauseYouWatchedUsesTheThreeMostRecentVideosAndMixTheNewest() = runTest(timeout = 30.seconds) {
        watch(video("a1"), watchedAtMillis = 1)
        watch(video("a2"), watchedAtMillis = 2)
        watch(video("a3"), watchedAtMillis = 3)
        watch(video("a4"), watchedAtMillis = 4)

        val rows = viewModel().loaded().rows

        val becauseRows = rows.filter { it.section == HomeSection.BecauseYouWatched }
        assertEquals(listOf("Video a4", "Video a3", "Video a2"), becauseRows.map { it.seedTitle })
        assertTrue(becauseRows.all { it.items.isNotEmpty() })
        val mixRows = rows.filter { it.section == HomeSection.Mix }
        assertEquals(listOf("Video a4"), mixRows.map { it.seedTitle })
    }

    @Test
    fun continueWatchingShowsOnlyPartlyWatchedVideosWithProgress() = runTest(timeout = 30.seconds) {
        watch(video("notStarted", durationSeconds = 600), watchedAtMillis = 1, positionMillis = 0)
        watch(video("half", durationSeconds = 600), watchedAtMillis = 2, positionMillis = 300_000)
        watch(video("finished", durationSeconds = 600), watchedAtMillis = 3, positionMillis = 595_000)

        val continueRow = viewModel().loaded().rows.single { it.section == HomeSection.ContinueWatching }

        assertEquals(listOf("Video half"), continueRow.items.map { it.video.title })
        assertEquals(0.5f, continueRow.items.single().watchedFraction, 0.01f)
    }

    @Test
    fun musicRowIsLeftOutWhereYoutubeChartsHasNoData() = runTest(timeout = 30.seconds) {
        val rows = viewModel(testSettings(contentCountry = "BD").value).loaded().rows

        assertEquals(listOf(HomeSection.TrendingMovies), rows.map { it.section })
        assertFalse(TrendingKind.Music in content.trendingRequests)
    }

    @Test
    fun aFailingSourceRemovesOnlyItsOwnRow() = runTest(timeout = 30.seconds) {
        content.failures += "trending:Music"

        val state = viewModel().loaded()

        assertEquals(listOf(HomeSection.TrendingMovies), state.rows.map { it.section })
        assertFalse(state.hasError)
    }

    @Test
    fun nothingLoadedShowsAnErrorAndRefreshRecovers() = runTest(timeout = 30.seconds) {
        content.failures += setOf("trending:Music", "trending:MoviesAndShows")
        val viewModel = viewModel()

        val failed = viewModel.loaded()
        assertTrue(failed.hasError)
        assertTrue(failed.rows.isEmpty())

        content.failures.clear()
        viewModel.refresh()
        val recovered = viewModel.state.first { !it.isLoading && it.rows.isNotEmpty() }
        assertFalse(recovered.hasError)
    }
}
