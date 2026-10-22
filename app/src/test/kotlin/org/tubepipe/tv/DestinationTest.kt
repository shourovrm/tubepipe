package org.tubepipe.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationTest {
    @Test
    fun drawerLeavesOneSlotForThePinnedSavedSearch() {
        assertTrue(Destination.entries.size < Destination.MAXIMUM_DRAWER_SLOTS)
    }

    @Test
    fun searchComesFirstSoItIsOneStepUpFromHome() {
        assertEquals(
            listOf("Search", "Home", "Trending", "Subscriptions", "Library", "Settings"),
            Destination.entries.map { it.name },
        )
    }
}
