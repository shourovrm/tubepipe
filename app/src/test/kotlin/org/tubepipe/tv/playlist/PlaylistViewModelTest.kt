package org.tubepipe.tv.playlist

import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tubepipe.core.database.PlaylistEntity
import org.tubepipe.tv.common.toPlaylistVideo
import org.tubepipe.tv.navigation.PlaylistSource
import org.tubepipe.tv.testing.FakeContentRepository
import org.tubepipe.tv.testing.MainDispatcherRule
import org.tubepipe.tv.testing.TestDatabaseRule
import org.tubepipe.tv.testing.video
import org.tubepipe.tv.testing.videos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val databaseRule = TestDatabaseRule()

    private val playlistUrl = "https://www.youtube.com/playlist?list=PL1"
    private val content = FakeContentRepository()

    private fun viewModel(source: PlaylistSource) = PlaylistViewModel(
        source = source,
        content = content,
        playlistDao = databaseRule.database.playlistDao(),
        likedPlaylistTitle = "Liked",
        random = Random(seed = 7),
    )

    @Test
    fun remotePlaylistShowsHeaderAndVideos() = runTest(timeout = 30.seconds) {
        content.addPlaylist(playlistUrl, "Road trip", videos("trip", 3), uploaderName = "Alex")

        val state = viewModel(PlaylistSource.Remote(playlistUrl)).state.first { !it.isLoading }

        assertEquals("Road trip", state.title)
        assertEquals("Alex", state.ownerName)
        assertEquals(3L, state.videoCount)
        assertEquals(videos("trip", 3), state.videos)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun remotePlaylistLoadsTheNextPage() = runTest(timeout = 30.seconds) {
        content.addPlaylist(playlistUrl, "Road trip", videos("trip", 2))
        content.playlistSecondPages[playlistUrl] = videos("more", 2)
        val viewModel = viewModel(PlaylistSource.Remote(playlistUrl))
        assertTrue(viewModel.state.first { !it.isLoading }.canLoadMore)

        viewModel.loadMore()

        val state = viewModel.state.first { it.videos.size == 4 }
        assertEquals(videos("trip", 2) + videos("more", 2), state.videos)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun failedRemoteLoadShowsErrorAndRetryRecovers() = runTest(timeout = 30.seconds) {
        content.addPlaylist(playlistUrl, "Road trip", videos("trip", 1))
        content.failures += "playlist:$playlistUrl"
        val viewModel = viewModel(PlaylistSource.Remote(playlistUrl))
        assertTrue(viewModel.state.first { !it.isLoading }.hasError)

        content.failures.clear()
        viewModel.retry()

        val state = viewModel.state.first { !it.isLoading && !it.hasError }
        assertEquals(1, state.videos.size)
    }

    @Test
    fun localPlaylistShowsItsStoredVideosInOrder() = runTest(timeout = 30.seconds) {
        val dao = databaseRule.database.playlistDao()
        val playlistId = dao.createPlaylist("Favourites", createdAtMillis = 1)
        dao.addItem(playlistId, video("a").toPlaylistVideo())
        dao.addItem(playlistId, video("b").toPlaylistVideo())

        val state = viewModel(PlaylistSource.Local(playlistId)).state.first { it.videos.size == 2 }

        assertEquals("Favourites", state.title)
        assertNull(state.ownerName)
        assertEquals(listOf(video("a").url, video("b").url), state.videos.map { it.url })
    }

    @Test
    fun likedPlaylistUsesTheDisplayTitle() = runTest(timeout = 30.seconds) {
        val state = viewModel(PlaylistSource.Local(PlaylistEntity.LIKED_ID)).state.first { it.title.isNotEmpty() }

        assertEquals("Liked", state.title)
    }

    @Test
    fun queueStartsAtTheChosenVideo() = runTest(timeout = 30.seconds) {
        content.addPlaylist(playlistUrl, "Road trip", videos("trip", 3))
        val source = PlaylistSource.Remote(playlistUrl)
        val viewModel = viewModel(source)
        viewModel.state.first { !it.isLoading }

        val queue = viewModel.queueStartingAt(2)

        assertEquals(videos("trip", 3).map { it.url }, queue?.videoUrls)
        assertEquals(2, queue?.index)
        assertEquals(source, queue?.source)
        assertEquals("Road trip", queue?.title)
        assertNull(viewModel.queueStartingAt(3))
    }

    @Test
    fun shuffledQueueHoldsTheSameVideosFromTheFirstEntry() = runTest(timeout = 30.seconds) {
        content.addPlaylist(playlistUrl, "Road trip", videos("trip", 10))
        val viewModel = viewModel(PlaylistSource.Remote(playlistUrl))
        viewModel.state.first { !it.isLoading }

        val queue = viewModel.shuffledQueue()!!

        val originalUrls = videos("trip", 10).map { it.url }
        assertEquals(0, queue.index)
        assertEquals(originalUrls.toSet(), queue.videoUrls.toSet())
        assertEquals(originalUrls.size, queue.videoUrls.size)
        assertTrue("a seeded shuffle of 10 should change the order", queue.videoUrls != originalUrls)
    }

    @Test
    fun emptyPlaylistHasNoQueue() = runTest(timeout = 30.seconds) {
        val playlistId = databaseRule.database.playlistDao().createPlaylist("Empty", createdAtMillis = 1)
        val viewModel = viewModel(PlaylistSource.Local(playlistId))
        viewModel.state.first { !it.isLoading }

        assertNull(viewModel.queueStartingAt(0))
        assertNull(viewModel.shuffledQueue())
    }
}
