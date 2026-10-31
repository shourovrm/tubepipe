package org.tubepipe.core.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryTest {
    @Test
    fun aDecodingFailureIsADecoderError() {
        assertTrue(isDecoderError(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }

    @Test
    fun anExpiredUrlIsNotADecoderError() {
        // YouTube answers an expired stream URL with HTTP 403 or 410, which Media3 reports as a bad HTTP status.
        assertFalse(isDecoderError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
    }

    @Test
    fun aDroppedNetworkIsNotADecoderError() {
        assertFalse(isDecoderError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
    }

    @Test
    fun theLowerRenditionIsTheTallestOptionBelowTheShownHeight() {
        assertEquals(480, lowerRendition(listOf(1080, 720, 480, 360), 720))
    }

    @Test
    fun theOptionsCanBeInAnyOrder() {
        assertEquals(720, lowerRendition(listOf(360, 1080, 720), 1080))
    }

    @Test
    fun noLowerRenditionWhenTheShownHeightIsTheLowest() {
        assertNull(lowerRendition(listOf(360, 720), 360))
    }

    @Test
    fun noLowerRenditionWhenNoOptionsAreKnown() {
        assertNull(lowerRendition(emptyList(), 720))
    }
}
