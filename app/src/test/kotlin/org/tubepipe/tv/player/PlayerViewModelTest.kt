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
import org.tubepipe.core.player.PreferredCodec
import org.tubepipe.tv.data.SponsorBlockCategory
import org.tubepipe.tv.data.VideoCodec
import org.tubepipe.tv.data.VideoQuality
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

    private val settings = testSettings()

    private fun viewModel(sponsorBlockEnabled: Boolean = true) = PlayerViewModel(
        videoUrl = playing.url,
        content = content,
        history = databaseRule.database.historyDao(),
        playlists = databaseRule.database.playlistDao(),
        settings = settings.apply { value = value.copy(sponsorBlockEnabled = sponsorBlockEnabled) },
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

        // savePosition writes in the background (the player must not wait on disk), so wait for the row.
        val saved = databaseRule.database.historyDao().observeRecent(10).first { entries ->
            entries.any { it.url == playing.url && it.positionMillis == 200_000L }
        }
        assertEquals(200_000L, saved.single { it.url == playing.url }.positionMillis)
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

    @Test
    fun watchHistoryOffRecordsNothing() = runTest(timeout = 30.seconds) {
        settings.value = settings.value.copy(watchHistoryEnabled = false)
        val viewModel = viewModel()
        viewModel.state.first { !it.isLoading && it.details != null }
        viewModel.savePosition(100_000L)

        assertTrue(databaseRule.database.historyDao().observeRecent(10).first().isEmpty())
    }

    @Test
    fun resumeOffOrAlwaysFromTheStartIgnoresTheSavedPosition() = runTest(timeout = 30.seconds) {
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
        settings.value = settings.value.copy(alwaysStartFromBeginning = true)

        assertNull(viewModel().state.first { !it.isLoading && it.details != null }.resumePositionMillis)
    }

    @Test
    fun onlyTheChosenSponsorBlockCategoriesAreRequested() = runTest(timeout = 30.seconds) {
        settings.value = settings.value.copy(sponsorBlockCategories = setOf(SponsorBlockCategory.Sponsor))
        viewModel().state.first { it.segments.isNotEmpty() }

        assertEquals(setOf(SponsorBlockCategory.Sponsor), sponsorSegments.requestedCategories)
    }

    @Test
    fun skipStepComesFromSettings() = runTest(timeout = 30.seconds) {
        settings.value = settings.value.copy(seekStepSeconds = 30)
        val viewModel = viewModel()

        assertEquals(30_000L, viewModel.state.first { !it.isLoading && it.details != null }.seekStepMillis)
    }

    @Test
    fun nextVideoIsTheFirstRelatedOneAndAutoplayCanBeOff() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.state.first { !it.isLoading && it.details != null }
        assertEquals(videos("rel", 10).first().url, viewModel.nextVideoUrl())
        assertEquals(videos("rel", 10).first().url, viewModel.videoToAutoplayAfterEnd())

        settings.value = settings.value.copy(autoplayNext = false)
        assertNull(viewModel.state.first { !it.autoplayNext }.let { viewModel.videoToAutoplayAfterEnd() })
    }

    @Test
    fun playbackPreferencesFollowSettings() = runTest(timeout = 30.seconds) {
        settings.value = settings.value.copy(
            defaultQuality = VideoQuality.P720,
            preferredCodec = VideoCodec.Vp9,
            defaultSpeed = 1.25f,
            captionsEnabled = true,
            preferredAudioLanguage = "bn",
        )
        val preferences = viewModel().playbackPreferences()

        assertEquals(720, preferences.maxVideoHeight)
        assertEquals(PreferredCodec.Vp9, preferences.preferredCodec)
        assertEquals(1.25f, preferences.speed, 0.001f)
        assertEquals("bn", preferences.preferredAudioLanguage)
        assertTrue("captions on means a caption language is preferred", preferences.captionLanguage != null)
    }
}

private class FakeSponsorBlock(private val segments: List<SkipSegment>) : SponsorBlockSource {
    var requestedVideoId: String? = null
    var requestedCategories: Set<SponsorBlockCategory>? = null

    override suspend fun segments(videoId: String, categories: Set<SponsorBlockCategory>): List<SkipSegment> {
        requestedVideoId = videoId
        requestedCategories = categories
        return segments
    }
}
