package org.tubepipe.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackClientOrderTest {
    @Test
    fun visionOsIsTheDefaultAndFallsBackToMweb() {
        assertEquals(PlayerClient.VisionOs, PlayerClient.DEFAULT)
        assertEquals(
            listOf(PlayerClient.VisionOs, PlayerClient.Mweb),
            PlayerClient.fallbackOrder(PlayerClient.VisionOs),
        )
    }

    @Test
    fun choosingMwebInSettingsFallsBackToVisionOs() {
        assertEquals(listOf(PlayerClient.Mweb, PlayerClient.VisionOs), PlayerClient.fallbackOrder(PlayerClient.Mweb))
    }

    @Test
    fun extractorNamesMatchPipePipe() {
        assertEquals("visionos", PlayerClient.VisionOs.extractorName)
        assertEquals("mweb", PlayerClient.Mweb.extractorName)
    }
}
