package org.tubepipe.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowQualityOfferTest {
    @Test
    fun onlyTheMuxed360pStreamIsLowQuality() {
        // What VisionOS leaves for "made for kids" videos: YouTube refuses the request and the extractor keeps
        // only the Android client's single muxed itag 18.
        assertTrue(LowQualityOffer.isLowQualityOnly(hasMasterPlaylist = false, videoHeights = listOf(360)))
    }

    @Test
    fun noVideoStreamAtAllIsNotLowQuality() {
        // Nothing to compare against; the normal failure path handles an empty offer.
        assertFalse(LowQualityOffer.isLowQualityOnly(hasMasterPlaylist = false, videoHeights = emptyList()))
    }

    @Test
    fun aMasterPlaylistIsNotLowQuality() {
        assertFalse(LowQualityOffer.isLowQualityOnly(hasMasterPlaylist = true, videoHeights = listOf(360)))
    }

    @Test
    fun anyStreamAbove360pIsNotLowQuality() {
        assertFalse(LowQualityOffer.isLowQualityOnly(hasMasterPlaylist = false, videoHeights = listOf(360, 720)))
    }
}
