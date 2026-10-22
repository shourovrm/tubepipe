package org.tubepipe.tv.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tubepipe.tv.Destination

class AppNavigatorTest {
    private var nowMillis = 0L
    private val navigator = AppNavigator(clock = { nowMillis })

    @Test
    fun startsOnHome() {
        assertEquals(Screen.Top(Destination.Home), navigator.current)
    }

    @Test
    fun drawerSwitchReplacesTheTopLevelScreen() {
        navigator.openDestination(Destination.Trending)
        navigator.openDestination(Destination.Search)
        assertEquals(Screen.Top(Destination.Search), navigator.current)

        // Back from any top-level screen other than Home goes to Home, not through every tab visited.
        assertTrue(navigator.back())
        assertEquals(Screen.Top(Destination.Home), navigator.current)
    }

    @Test
    fun backFromAVideoGoesHome() {
        navigator.openDestination(Destination.Trending)
        navigator.openChannel("https://www.youtube.com/channel/A")
        navigator.openVideo("https://www.youtube.com/watch?v=x")

        assertTrue(navigator.back())
        assertEquals(Screen.Top(Destination.Home), navigator.current)
    }

    @Test
    fun backFromTheChannelOfThePlayingVideoReturnsToTheVideo() {
        navigator.openVideo("https://www.youtube.com/watch?v=x")
        navigator.openChannel("https://www.youtube.com/channel/A")

        assertTrue(navigator.isChannelOverPlayer)
        assertTrue(navigator.back())
        assertEquals(Screen.Player("https://www.youtube.com/watch?v=x"), navigator.current)
    }

    @Test
    fun aChannelOpenedFromHomeIsNotOverThePlayer() {
        navigator.openChannel("https://www.youtube.com/channel/A")

        assertFalse(navigator.isChannelOverPlayer)
    }

    @Test
    fun backFromAPlaylistVideoReturnsToThePlaylist() {
        val playlist = PlaylistSource.Remote("https://www.youtube.com/playlist?list=P")
        navigator.openPlaylist(playlist)
        navigator.playQueue(queue(index = 1, source = playlist))

        assertTrue(navigator.back())
        assertEquals(Screen.Playlist(playlist), navigator.current)
    }

    @Test
    fun theNextQueueEntryReplacesThePlayerSoBackStillReachesThePlaylist() {
        val playlist = PlaylistSource.Local(playlistId = 7)
        navigator.openPlaylist(playlist)
        navigator.playQueue(queue(index = 0, source = playlist))

        navigator.playQueue(queue(index = 1, source = playlist))

        assertEquals("https://www.youtube.com/watch?v=1", (navigator.current as Screen.Player).videoUrl)
        assertTrue(navigator.back())
        assertEquals(Screen.Playlist(playlist), navigator.current)
    }

    @Test
    fun aRelatedVideoLeavesThePlaylistAndBackThenGoesHome() {
        val playlist = PlaylistSource.Local(playlistId = 7)
        navigator.openPlaylist(playlist)
        navigator.playQueue(queue(index = 0, source = playlist))

        navigator.openVideo("https://www.youtube.com/watch?v=related")

        assertEquals(Screen.Player("https://www.youtube.com/watch?v=related"), navigator.current)
        assertTrue(navigator.back())
        assertEquals(Screen.Top(Destination.Home), navigator.current)
    }

    @Test
    fun queueStepsStopAtBothEnds() {
        val last = queue(index = 2)
        val first = queue(index = 0)

        assertEquals(null, last.next())
        assertEquals(null, first.previous())
        assertEquals("https://www.youtube.com/watch?v=1", first.next()?.currentUrl)
    }

    private fun queue(index: Int, source: PlaylistSource = PlaylistSource.Local(playlistId = 1)) = PlaybackQueue(
        source = source,
        title = "Mix",
        videoUrls = List(3) { number -> "https://www.youtube.com/watch?v=$number" },
        index = index,
    )

    @Test
    fun openingAnotherVideoFromThePlayerReplacesIt() {
        navigator.openVideo("https://www.youtube.com/watch?v=a")
        navigator.openVideo("https://www.youtube.com/watch?v=b")

        assertEquals(Screen.Player("https://www.youtube.com/watch?v=b"), navigator.current)
        assertTrue(navigator.back())
        assertEquals(Screen.Top(Destination.Home), navigator.current)
    }

    @Test
    fun doubleBackOnHomeExitsSingleBackDoesNot() {
        nowMillis = 10_000
        assertTrue("first Back on Home is consumed", navigator.back())
        nowMillis = 11_000
        assertFalse("second Back within the window lets the app exit", navigator.back())
    }

    @Test
    fun slowSecondBackOnHomeDoesNotExit() {
        nowMillis = 10_000
        assertTrue(navigator.back())
        nowMillis = 20_000
        assertTrue(navigator.back())
    }

    @Test
    fun searchOpensTheSearchTabWithAQuery() {
        navigator.openSearch("cats")
        assertEquals(Screen.Top(Destination.Search), navigator.current)
        assertEquals("cats", navigator.pendingSearchQuery)
    }
}
