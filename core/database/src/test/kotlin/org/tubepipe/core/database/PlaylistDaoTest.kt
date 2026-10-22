package org.tubepipe.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistDaoTest {
    @get:Rule
    val databaseRule = DatabaseTestRule()
    private val playlists get() = databaseRule.database.playlistDao()

    private fun item(url: String) = PlaylistVideo(
        url = url,
        title = "Title $url",
        channelName = "Channel",
        thumbnailUrl = null,
        durationSeconds = 60L,
    )

    @Test
    fun freshDatabaseHasTheBuiltInLikedPlaylist() = runTest {
        val all = playlists.observePlaylists().first()
        assertTrue(all.any { it.id == PlaylistEntity.LIKED_ID })
    }

    @Test
    fun itemsKeepTheOrderTheyWereAddedIn() = runTest {
        val playlistId = playlists.createPlaylist(name = "Later", createdAtMillis = 1L)
        playlists.addItem(playlistId, item("a"))
        playlists.addItem(playlistId, item("b"))
        playlists.addItem(playlistId, item("c"))

        assertEquals(listOf("a", "b", "c"), playlists.observeItems(playlistId).first().map { it.url })
    }

    @Test
    fun addingTheSameVideoTwiceKeepsOneCopy() = runTest {
        playlists.addItem(PlaylistEntity.LIKED_ID, item("a"))
        playlists.addItem(PlaylistEntity.LIKED_ID, item("a"))

        assertEquals(listOf("a"), playlists.observeItems(PlaylistEntity.LIKED_ID).first().map { it.url })
    }

    @Test
    fun removeItemAndDeletePlaylist() = runTest {
        val playlistId = playlists.createPlaylist(name = "Later", createdAtMillis = 1L)
        playlists.addItem(playlistId, item("a"))
        playlists.addItem(playlistId, item("b"))

        playlists.removeItem(playlistId, "a")
        assertEquals(listOf("b"), playlists.observeItems(playlistId).first().map { it.url })

        playlists.deletePlaylist(playlistId)
        assertTrue(playlists.observePlaylists().first().none { it.id == playlistId })
        assertTrue(playlists.observeItems(playlistId).first().isEmpty())
    }

    @Test
    fun isInPlaylistReflectsMembership() = runTest {
        assertEquals(false, playlists.observeContains(PlaylistEntity.LIKED_ID, "a").first())
        playlists.addItem(PlaylistEntity.LIKED_ID, item("a"))
        assertEquals(true, playlists.observeContains(PlaylistEntity.LIKED_ID, "a").first())
    }

    // "New playlist" in the player's add-to-playlist menu creates the playlist with the playing video already in it.
    @Test
    fun createPlaylistWithVideoHoldsThatVideo() = runTest {
        val playlistId = playlists.createPlaylistWithVideo(
            name = "Watch later",
            video = item("a"),
            createdAtMillis = 5L,
        )

        val created = playlists.observePlaylists().first().single { it.id == playlistId }
        assertEquals("Watch later", created.name)
        assertEquals(listOf("a"), playlists.observeItems(playlistId).first().map { it.url })
    }
}
