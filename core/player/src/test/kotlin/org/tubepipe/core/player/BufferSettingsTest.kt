package org.tubepipe.core.player

import org.junit.Assert.assertTrue
import org.junit.Test

/** Starts above Media3's 1 s default but under ExoPlayer 2's 2.5 s; rebuffers wait for ExoPlayer 2's 5 s (player.md). */
class BufferSettingsTest {
    @Test
    fun startGateIsBetweenMedia3AndExoPlayer2Defaults() {
        assertTrue(BufferSettings.BUFFER_FOR_PLAYBACK_MS in 1_001..2_499)
        assertTrue(BufferSettings.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS >= 5_000)
    }

    @Test
    fun backBufferAllowsSeekingBackThirtySecondsWithoutRefetching() {
        assertTrue(BufferSettings.BACK_BUFFER_MS >= 30_000)
    }
}
