package org.tubepipe.tv.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** The settings sliders move through a fixed list of choices with Left and Right. */
class SettingsControlsTest {
    private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    @Test
    fun rightMovesToTheNextChoice() {
        assertEquals(1.25f, steppedChoice(speeds, current = 1f, step = 1))
    }

    @Test
    fun leftMovesToThePreviousChoice() {
        assertEquals(0.75f, steppedChoice(speeds, current = 1f, step = -1))
    }

    @Test
    fun theEndsDoNotWrapAround() {
        assertEquals(2f, steppedChoice(speeds, current = 2f, step = 1))
        assertEquals(0.5f, steppedChoice(speeds, current = 0.5f, step = -1))
    }

    @Test
    fun aValueNotInTheListStartsFromTheNearestChoice() {
        // A stored 1.1x (from an older version) sits nearest 1x, so Right goes to 1.25x.
        assertEquals(1.25f, steppedChoice(speeds, current = 1.1f, step = 1))
    }

    @Test
    fun seekStepsUseTheSameRule() {
        assertEquals(15, steppedChoice(listOf(5, 10, 15, 20, 25, 30), current = 10, step = 1))
    }
}
