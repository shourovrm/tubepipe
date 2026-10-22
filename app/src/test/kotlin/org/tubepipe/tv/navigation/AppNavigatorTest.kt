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
    fun backReturnsFromPlayerToTheScreenItWasOpenedFrom() {
        navigator.openDestination(Destination.Trending)
        navigator.openChannel("https://www.youtube.com/channel/A")
        navigator.openVideo("https://www.youtube.com/watch?v=x")

        assertTrue(navigator.back())
        assertEquals(Screen.Channel("https://www.youtube.com/channel/A"), navigator.current)
        assertTrue(navigator.back())
        assertEquals(Screen.Top(Destination.Trending), navigator.current)
    }

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
