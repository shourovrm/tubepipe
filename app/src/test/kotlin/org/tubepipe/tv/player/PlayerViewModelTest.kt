package org.tubepipe.tv.player

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tubepipe.core.database.HistoryEntity
import org.tubepipe.core.database.PlaylistEntity
import org.tubepipe.tv.testing.FakeContentRepository
import org.tubepipe.tv.testing.MainDispatcherRule
import org.tubepipe.tv.testing.TestDatabaseRule
import org.tubepipe.tv.testing.testSettings
import org.tubepipe.tv.testing.video
import org.tubepipe.tv.testing.videos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val databaseRule = TestDatabaseRule()

    private val playing = video("dQw4w9WgXcQ", durationSeconds = 600)
    private val content = FakeContentRepository().apply { addDetails(playing, related = videos("rel", 10)) }
    private val sponsorSegments = FakeSponsorBlock(
        listOf(SkipSegment(startMillis = 10_000, endMillis = 25_000, category = "sponsor")),
    )

    private fun viewModel(sponsorBlockEnabled: Boolean = true) = PlayerViewModel(
        videoUrl = playing.url,
        content = content,
        history = databaseRule.database.historyDao(),
        playlists = databaseRule.database.playlistDao(),
        settings = testSettings(sponsorBlockEnabled = sponsorBlockEnabled),
        sponsorBlock = sponsorSegments,
        clock = { 7_000L },
    )

    @Test
    fun detailsAndRelatedVideosLoad() = runTest(timeout = 30.seconds) {
        val state = viewModel().state.first { !it.isLoading && it.details != null }

        assertEquals(playing.title, state.details?.video?.title)
        assertEquals(videos("rel", 10), state.details?.related)
    }

    @Test
    fun resumesWhereThePreviousViewStopped() = runTest(timeout = 30.seconds) {
        databaseRule.database.historyDao().upsert(
            HistoryEntity(
                playing.url,
                playing.title,
                "C",
                null,
                600L,
                lastWatchedAtMillis = 1,
                positionMillis = 120_000,
            ),
        )

        val state = viewModel().state.first { !it.isLoading && it.details != null }

        assertEquals(120_000L, state.resumePositionMillis)
    }

    @Test
    fun aFinishedVideoStartsFromTheBeginning() = runTest(timeout = 30.seconds) {
        databaseRule.database.historyDao().upsert(
            HistoryEntity(
                playing.url,
                playing.title,
                "C",
                null,
                600L,
                lastWatchedAtMillis = 1,
                positionMillis = 590_000,
            ),
        )

        val state = viewModel().state.first { !it.isLoading && it.details != null }

        assertNull(state.resumePositionMillis)
    }

    @Test
    fun openingTheVideoRecordsItInHistory() = runTest(timeout = 30.seconds) {
        viewModel().state.first { !it.isLoading && it.details != null }

        val entry = databaseRule.database.historyDao().observeRecent(10).first { it.isNotEmpty() }.single()
        assertEquals(playing.url, entry.url)
        assertEquals(7_000L, entry.lastWatchedAtMillis)
    }

    @Test
    fun savedPositionIsStored() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.state.first { !it.isLoading && it.details != null }

        viewModel.savePosition(200_000L)

        assertEquals(200_000L, databaseRule.database.historyDao().positionOf(playing.url))
    }

    @Test
    fun likeTogglesTheLikedPlaylist() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.state.first { !it.isLoading && it.details != null }

        viewModel.toggleLike()
        assertTrue(viewModel.state.first { it.isLiked }.isLiked)
        assertEquals(
            listOf(playing.url),
            databaseRule.database.playlistDao().observeItems(PlaylistEntity.LIKED_ID).first().map { it.url },
        )

        viewModel.toggleLike()
        viewModel.state.first { !it.isLiked }
    }

    @Test
    fun sponsorSegmentIsSkippedToItsEnd() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.state.first { it.segments.isNotEmpty() }

        assertEquals(25_000L, viewModel.skipTargetFor(positionMillis = 12_000))
        assertNull(viewModel.skipTargetFor(positionMillis = 5_000))
        assertNull(viewModel.skipTargetFor(positionMillis = 25_000))
        assertEquals("dQw4w9WgXcQ", sponsorSegments.requestedVideoId)
    }

    @Test
    fun sponsorBlockOffSkipsNothing() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel(sponsorBlockEnabled = false)
        viewModel.state.first { !it.isLoading && it.details != null }

        assertNull(viewModel.skipTargetFor(positionMillis = 12_000))
    }
}

private class FakeSponsorBlock(private val segments: List<SkipSegment>) : SponsorBlockSource {
    var requestedVideoId: String? = null

    override suspend fun segments(videoId: String): List<SkipSegment> {
        requestedVideoId = videoId
        return segments
    }
}
