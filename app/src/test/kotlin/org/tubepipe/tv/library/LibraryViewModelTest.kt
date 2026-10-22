package org.tubepipe.tv.library

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tubepipe.core.database.HistoryEntity
import org.tubepipe.core.database.PlaylistEntity
import org.tubepipe.tv.common.toPlaylistVideo
import org.tubepipe.tv.testing.MainDispatcherRule
import org.tubepipe.tv.testing.TestDatabaseRule
import org.tubepipe.tv.testing.video

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val databaseRule = TestDatabaseRule()

    private val database get() = databaseRule.database

    private fun viewModel() = LibraryViewModel(
        history = database.historyDao(),
        playlists = database.playlistDao(),
        searches = database.searchDao(),
        clock = { 9_000L },
    )

    @Test
    fun historyIsNewestFirstWithWatchedProgress() = runTest(timeout = 30.seconds) {
        database.historyDao().upsert(
            HistoryEntity("a", "Old", "C", null, 100L, lastWatchedAtMillis = 1, positionMillis = 25_000),
        )
        database.historyDao().upsert(
            HistoryEntity("b", "New", "C", null, 100L, lastWatchedAtMillis = 2, positionMillis = 0),
        )

        val history = viewModel().state.first { it.history.size == 2 }.history

        assertEquals(listOf("New", "Old"), history.map { it.video.title })
        assertEquals(0.25f, history[1].watchedFraction, 0.01f)
    }

    @Test
    fun clearHistoryEmptiesIt() = runTest(timeout = 30.seconds) {
        database.historyDao().upsert(
            HistoryEntity("a", "Old", "C", null, 100L, lastWatchedAtMillis = 1, positionMillis = 0),
        )
        val viewModel = viewModel()
        viewModel.state.first { it.history.isNotEmpty() }

        viewModel.clearHistory()

        assertTrue(viewModel.state.first { it.history.isEmpty() }.history.isEmpty())
    }

    @Test
    fun likedComesFirstAndCannotBeDeleted() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.createPlaylist("Watch later")

        val playlists = viewModel.state.first { it.playlists.size == 2 }.playlists
        assertEquals(PlaylistEntity.LIKED_ID, playlists.first().id)

        viewModel.deletePlaylist(PlaylistEntity.LIKED_ID)
        viewModel.deletePlaylist(playlists[1].id)
        val remaining = viewModel.state.first { it.playlists.size == 1 }.playlists
        assertEquals(listOf(PlaylistEntity.LIKED_ID), remaining.map { it.id })
    }

    @Test
    fun openingAPlaylistShowsItsVideosInOrder() = runTest(timeout = 30.seconds) {
        database.playlistDao().addItem(PlaylistEntity.LIKED_ID, video("x").toPlaylistVideo())
        database.playlistDao().addItem(PlaylistEntity.LIKED_ID, video("y").toPlaylistVideo())
        val viewModel = viewModel()

        viewModel.openPlaylist(PlaylistEntity.LIKED_ID)

        val opened = viewModel.state.first { it.openedPlaylistVideos.size == 2 }
        assertEquals(PlaylistEntity.LIKED_ID, opened.openedPlaylistId)
        assertEquals(listOf("Video x", "Video y"), opened.openedPlaylistVideos.map { it.title })

        viewModel.closePlaylist()
        assertEquals(null, viewModel.state.first { it.openedPlaylistId == null }.openedPlaylistId)
    }

    @Test
    fun savedSearchesCanBePinnedUnpinnedAndDeleted() = runTest(timeout = 30.seconds) {
        database.searchDao().saveSearch("cats")
        val viewModel = viewModel()
        viewModel.state.first { it.savedSearches.isNotEmpty() }

        viewModel.pinSavedSearch("cats")
        assertTrue(
            viewModel.state.first {
                it.savedSearches.any { search -> search.pinned }
            }.savedSearches.single().pinned,
        )

        viewModel.unpinSavedSearch()
        viewModel.state.first { it.savedSearches.none { search -> search.pinned } }

        viewModel.deleteSavedSearch("cats")
        assertTrue(viewModel.state.first { it.savedSearches.isEmpty() }.savedSearches.isEmpty())
    }
}
