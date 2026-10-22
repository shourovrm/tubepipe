package org.tubepipe.core.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.tubepipe.core.service.ExtractorInitializer

/**
 * Real playback on the TV box (exclusive resource, see AGENTS.md). Each test prints one
 * PLAYBACK_RESULT line for the decision log.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackOnDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var player: TubepipePlayer
    private lateinit var surfaceTexture: SurfaceTexture
    private var rebufferCount = 0
    private var droppedFrames = 0

    companion object {
        private const val VIDEO_URL = "https://www.youtube.com/watch?v=G-eNlqqkn1w"

        @BeforeClass
        @JvmStatic
        fun startExtractorAndRuntime() {
            ExtractorInitializer.initialize(languageCode = "en", countryCode = "US")
            runBlocking(Dispatchers.Main) {
                PlaybackRuntime.initialize(ApplicationProvider.getApplicationContext())
            }
        }
    }

    @Before
    fun createSurface() {
        surfaceTexture = SurfaceTexture(0)
    }

    @After
    fun releasePlayer() {
        runBlocking(Dispatchers.Main) {
            if (::player.isInitialized) {
                player.release()
            }
        }
        surfaceTexture.release()
    }

    private suspend fun createPlayer(client: PlayerClient) {
        player = TubepipePlayer(context, preferredClient = client)
        player.exoPlayer.setVideoSurface(Surface(surfaceTexture))
        var hasBeenReady = false
        player.exoPlayer.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
                    if (state == Player.STATE_READY) {
                        hasBeenReady = true
                    } else if (state == Player.STATE_BUFFERING && hasBeenReady) {
                        rebufferCount++
                    }
                }

                override fun onDroppedVideoFrames(
                    eventTime: AnalyticsListener.EventTime,
                    droppedFrameCount: Int,
                    elapsedMs: Long,
                ) {
                    droppedFrames += droppedFrameCount
                }
            },
        )
    }

    /** Polls on the main thread until [condition] holds or [timeoutMs] passes; returns the time taken. */
    private suspend fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Long {
        val startedAt = System.currentTimeMillis()
        while (!condition()) {
            val elapsed = System.currentTimeMillis() - startedAt
            check(elapsed < timeoutMs) {
                "Timed out after $timeoutMs ms; player error: ${player.exoPlayer.playerError}"
            }
            delay(100)
        }
        return System.currentTimeMillis() - startedAt
    }

    private suspend fun playFor(seconds: Int) {
        val target = player.exoPlayer.currentPosition + seconds * 1_000L
        waitUntil(timeoutMs = seconds * 1_000L + 15_000L) {
            check(player.exoPlayer.playerError == null) { "Playback failed: ${player.exoPlayer.playerError}" }
            player.exoPlayer.currentPosition >= target
        }
    }

    private fun printResult(name: String, loaded: LoadedStream, extra: String = "") {
        println(
            "PLAYBACK_RESULT test=$name client=${loaded.client} delivery=${loaded.delivery} " +
                "height=${player.exoPlayer.videoSize.height} rebuffers=$rebufferCount dropped=$droppedFrames $extra",
        )
    }

    @Test
    fun visionOsPlaysHlsForSixtySecondsWithoutRebuffering() = runBlocking(Dispatchers.Main) {
        createPlayer(PlayerClient.VisionOs)
        val loadStartedAt = System.currentTimeMillis()
        val loaded = player.load(VIDEO_URL)
        waitUntil(timeoutMs = 15_000) { player.exoPlayer.playbackState == Player.STATE_READY }
        val timeToReadyMs = System.currentTimeMillis() - loadStartedAt

        playFor(seconds = 60)
        printResult("hls", loaded, "timeToReadyMs=$timeToReadyMs")

        assertEquals(PlayerClient.VisionOs, loaded.client)
        assertEquals(Delivery.Hls, loaded.delivery)
        assertEquals(0, rebufferCount)
        assertTrue("height ${player.exoPlayer.videoSize.height}", player.exoPlayer.videoSize.height >= 720)
    }

    @Test
    fun mwebPlaysSabrForSixtySecondsWithoutRebuffering() = runBlocking(Dispatchers.Main) {
        createPlayer(PlayerClient.Mweb)
        val loaded = player.load(VIDEO_URL)
        waitUntil(timeoutMs = 20_000) { player.exoPlayer.playbackState == Player.STATE_READY }

        playFor(seconds = 60)
        printResult("sabr", loaded)

        assertEquals(PlayerClient.Mweb, loaded.client)
        assertEquals(Delivery.Sabr, loaded.delivery)
        assertEquals(0, rebufferCount)
        assertTrue("height ${player.exoPlayer.videoSize.height}", player.exoPlayer.videoSize.height >= 1080)
    }

    @Test
    fun seekingBackThirtySecondsResumesQuickly() = runBlocking(Dispatchers.Main) {
        createPlayer(PlayerClient.DEFAULT)
        val loaded = player.load(VIDEO_URL)
        waitUntil(timeoutMs = 15_000) { player.exoPlayer.playbackState == Player.STATE_READY }
        playFor(seconds = 40)

        player.exoPlayer.seekTo(player.exoPlayer.currentPosition - 30_000L)
        val resumeMs = waitUntil(timeoutMs = 3_000) { player.exoPlayer.playbackState == Player.STATE_READY }
        playFor(seconds = 10)
        printResult("seekBack", loaded, "resumeMs=$resumeMs")

        assertTrue(player.exoPlayer.playerError == null)
    }

    @Test
    fun loadStartsAtTheResumePosition() = runBlocking(Dispatchers.Main) {
        createPlayer(PlayerClient.DEFAULT)
        player.load(VIDEO_URL, startPositionMs = 90_000L)
        waitUntil(timeoutMs = 15_000) { player.exoPlayer.playbackState == Player.STATE_READY }

        val position = withContext(Dispatchers.Main) { player.exoPlayer.currentPosition }
        assertTrue("started at $position ms", position in 85_000L..100_000L)
    }
}
