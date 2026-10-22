package org.tubepipe.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControllerTest {
    private val createdEngines = mutableListOf<FakeEngine>()
    private val controller = PlaybackController(createEngine = { FakeEngine().also { createdEngines += it } })

    @Test
    fun openingAVideoShowsItFullScreenWithANewEngine() {
        val lease = controller.engineFor("a")

        assertTrue(lease.isNew)
        assertEquals(NowPlaying("a", PlayerMode.Fullscreen), controller.state.value)
    }

    @Test
    fun leavingThePlayerKeepsItPlayingInTheCorner() {
        controller.engineFor("a")

        controller.minimize()

        assertEquals(NowPlaying("a", PlayerMode.Mini), controller.state.value)
        assertFalse(createdEngines.single().released)
    }

    @Test
    fun reopeningTheSameVideoReusesTheRunningEngine() {
        val first = controller.engineFor("a")
        controller.minimize()

        val second = controller.engineFor("a")

        assertFalse(second.isNew)
        assertSame(first.engine, second.engine)
        assertEquals(PlayerMode.Fullscreen, controller.state.value?.mode)
    }

    @Test
    fun playingAnotherVideoReplacesTheMiniPlayer() {
        controller.engineFor("a")
        controller.minimize()

        val lease = controller.engineFor("b")

        assertTrue(lease.isNew)
        assertTrue("the old engine is released", createdEngines.first().released)
        assertEquals(NowPlaying("b", PlayerMode.Fullscreen), controller.state.value)
    }

    @Test
    fun stopReleasesTheEngineAndClearsTheMiniPlayer() {
        controller.engineFor("a")
        controller.minimize()

        controller.stop()

        assertNull(controller.state.value)
        assertTrue(createdEngines.single().released)
    }

    @Test
    fun expandReturnsTheMiniPlayerToFullScreen() {
        controller.engineFor("a")
        controller.minimize()

        controller.expand()

        assertEquals(PlayerMode.Fullscreen, controller.state.value?.mode)
    }

    @Test
    fun previousIsTheVideoPlayedBeforeTheCurrentOne() {
        controller.engineFor("a")
        controller.engineFor("b")
        controller.engineFor("c")

        assertEquals("b", controller.previousVideoUrl())
        controller.engineFor("b")
        assertEquals("a", controller.previousVideoUrl())
    }

    @Test
    fun pauseToggleReachesTheEngine() {
        controller.engineFor("a")
        controller.togglePause()
        assertFalse(createdEngines.single().playing)
        controller.togglePause()
        assertTrue(createdEngines.single().playing)
    }

    @Test
    fun toggleLoopRepeatsTheCurrentVideo() {
        controller.engineFor("a")

        controller.toggleLoop()

        assertTrue(controller.isLooping.value)
        assertTrue(createdEngines.single().repeatsVideo)
    }

    @Test
    fun loopCarriesOverToTheNextVideo() {
        controller.engineFor("a")
        controller.toggleLoop()

        controller.engineFor("b")

        assertTrue(createdEngines.last().repeatsVideo)
    }

    @Test
    fun stopTurnsLoopOff() {
        controller.engineFor("a")
        controller.toggleLoop()

        controller.stop()
        controller.engineFor("b")

        assertFalse(controller.isLooping.value)
        assertFalse(createdEngines.last().repeatsVideo)
    }
}

private class FakeEngine : PlayerEngine {
    var released = false
    var repeatsVideo = false
    var playing = true

    override val isPlaying: Boolean get() = playing

    override fun pause() {
        playing = false
    }

    override fun resume() {
        playing = true
    }

    override fun release() {
        released = true
    }

    override fun setLooping(isLooping: Boolean) {
        repeatsVideo = isLooping
    }
}
