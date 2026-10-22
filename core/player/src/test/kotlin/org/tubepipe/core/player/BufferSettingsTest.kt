package org.tubepipe.core.player

import org.junit.Assert.assertTrue
import org.junit.Test

/** Media3 starts at 1 s of buffer; ExoPlayer 2.18.7 waited for 2.5 s (docs/decisions/player.md Gotchas). */
class BufferSettingsTest {
    @Test
    fun startAndRebufferThresholdsMatchExoPlayer2Defaults() {
        assertTrue(BufferSettings.BUFFER_FOR_PLAYBACK_MS >= 2_500)
        assertTrue(BufferSettings.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS >= 5_000)
    }

    @Test
    fun backBufferAllowsSeekingBackThirtySecondsWithoutRefetching() {
        assertTrue(BufferSettings.BACK_BUFFER_MS >= 30_000)
    }
}
