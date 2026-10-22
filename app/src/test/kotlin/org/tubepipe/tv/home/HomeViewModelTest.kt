package org.tubepipe.tv.home

import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
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

    private val dayMillis = 24 * 60 * 60 * 1_000L
    private val nowMillis = 30 * dayMillis
    private val recommendationStore = InMemoryRecommendationStore()

    private fun viewModel(settings: AppSettings = testSettings().value) = HomeViewModel(
        content = content,
        history = databaseRule.database.historyDao(),
        subscriptions = databaseRule.database.subscriptionDao(),
        settings = MutableStateFlow(settings),
        recommendationStore = recommendationStore,
        clock = { nowMillis },
        random = Random(42),
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
                HomeSection.Recommended,
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
    fun recommendedIsOneShuffledRowFromVideosWatchedInTheLastThreeDays() = runTest(timeout = 30.seconds) {
        watch(video("r1"), watchedAtMillis = nowMillis - 1 * dayMillis)
        watch(video("r2"), watchedAtMillis = nowMillis - 2 * dayMillis)
        watch(video("o1"), watchedAtMillis = nowMillis - 10 * dayMillis)

        val rows = viewModel().loaded().rows

        val recommended = rows.single { it.section == HomeSection.Recommended }
        val urls = recommended.items.map { it.video.url }
        val fromRecent = (
            content.details.getValue(video("r1").url).related +
                content.details.getValue(video("r2").url).related
            )
            .map { it.url }
            .toSet()
        assertTrue("only related videos of the last 3 days", urls.all { it in fromRecent })
        assertTrue("both recent videos contribute", urls.any { "related-r1" in it } && urls.any { "related-r2" in it })
        assertEquals("no duplicates", urls.size, urls.toSet().size)
        assertTrue("shuffled, not seed by seed", urls != fromRecent.toList())
        assertEquals(null, recommended.seedTitle)
    }

    @Test
    fun recommendedFallsBackToTheMostRecentVideosWhenNothingWasWatchedLately() = runTest(timeout = 30.seconds) {
        watch(video("o1"), watchedAtMillis = nowMillis - 10 * dayMillis)
        watch(video("o2"), watchedAtMillis = nowMillis - 9 * dayMillis)

        val recommended = viewModel().loaded().rows.single { it.section == HomeSection.Recommended }

        assertTrue(recommended.items.isNotEmpty())
    }

    @Test
    fun recommendedLeavesOutVideosAlreadyWatched() = runTest(timeout = 30.seconds) {
        watch(video("r1"), watchedAtMillis = nowMillis - dayMillis)
        val alreadyWatched = content.details.getValue(video("r1").url).related.first()
        databaseRule.database.historyDao().upsert(historyEntry(alreadyWatched, watchedAtMillis = nowMillis - dayMillis))

        val recommended = viewModel().loaded().rows.single { it.section == HomeSection.Recommended }

        assertTrue(recommended.items.none { it.video.url == alreadyWatched.url })
    }

    @Test
    fun everySectionAppearsAtMostOnceEvenWithRepeatedTitles() = runTest(timeout = 30.seconds) {
        // Regression: two history videos with the same title produced two rows with the same key.
        watch(video("same1"), watchedAtMillis = nowMillis - dayMillis)
        watch(video("same2"), watchedAtMillis = nowMillis - dayMillis + 1)
        databaseRule.database.historyDao().upsert(
            historyEntry(video("same1"), nowMillis - dayMillis).copy(title = "Motu Patlu"),
        )
        databaseRule.database.historyDao().upsert(
            historyEntry(video("same2"), nowMillis - dayMillis + 1).copy(title = "Motu Patlu"),
        )

        val sections = viewModel().loaded().rows.map { it.section }

        assertEquals(sections.distinct(), sections)
    }

    @Test
    fun mixFollowsTheNewestVideo() = runTest(timeout = 30.seconds) {
        watch(video("a1"), watchedAtMillis = nowMillis - 2 * dayMillis)
        watch(video("a2"), watchedAtMillis = nowMillis - dayMillis)

        val mixRow = viewModel().loaded().rows.single { it.section == HomeSection.Mix }

        assertEquals("Video a2", mixRow.seedTitle)
    }

    @Test
    fun savedRecommendationsShowAtOnceWhileFreshOnesLoad() = runTest(timeout = 30.seconds) {
        watch(video("r1"), watchedAtMillis = nowMillis - dayMillis)
        recommendationStore.saved = videos("cached", 6)
        val gate = CompletableDeferred<Unit>()
        content.detailsGate = gate
        val viewModel = viewModel()

        val early = viewModel.state.first { state -> state.rows.any { it.section == HomeSection.Recommended } }
        assertEquals(
            videos("cached", 6).map { it.url },
            early.rows.single { it.section == HomeSection.Recommended }.items.map { it.video.url },
        )

        gate.complete(Unit)
        val fresh = viewModel.state.first { state ->
            state.rows.singleOrNull {
                it.section == HomeSection.Recommended
            }?.items?.any { "related-r1" in it.video.url } ==
                true
        }
        assertTrue(fresh.rows.isNotEmpty())
        assertTrue("fresh list is saved for the next start", recommendationStore.saved.any { "related-r1" in it.url })
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

private class InMemoryRecommendationStore : RecommendationStore {
    var saved: List<VideoItem> = emptyList()

    override suspend fun load(): List<VideoItem> = saved

    override suspend fun save(videos: List<VideoItem>) {
        saved = videos
    }
}
