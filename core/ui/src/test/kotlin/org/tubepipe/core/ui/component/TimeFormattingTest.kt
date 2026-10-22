package org.tubepipe.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormattingTest {
    @Test
    fun shortVideosShowMinutesAndSeconds() {
        assertEquals("0:07", TimeFormatting.duration(7))
        assertEquals("3:32", TimeFormatting.duration(212))
    }

    @Test
    fun longVideosShowHours() {
        assertEquals("1:00:00", TimeFormatting.duration(3_600))
        assertEquals("10:04:05", TimeFormatting.duration(36_245))
    }

    @Test
    fun watchedFractionIsClampedBetweenZeroAndOne() {
        assertEquals(0.5f, TimeFormatting.watchedFraction(positionMillis = 30_000, durationSeconds = 60), 0.001f)
        assertEquals(1f, TimeFormatting.watchedFraction(positionMillis = 90_000, durationSeconds = 60), 0.001f)
        assertEquals(0f, TimeFormatting.watchedFraction(positionMillis = 10_000, durationSeconds = null), 0.001f)
    }
}
