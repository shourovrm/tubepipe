package org.tubepipe.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoStreamChoiceTest {
    private val candidates = listOf(
        VideoCandidate(height = 2160, codec = "vp9", bitrate = 18_000_000),
        VideoCandidate(height = 1080, codec = "vp9", bitrate = 4_000_000),
        VideoCandidate(height = 1080, codec = "avc1.640028", bitrate = 5_000_000),
        VideoCandidate(height = 720, codec = "avc1.4d401f", bitrate = 2_500_000),
        VideoCandidate(height = 360, codec = "avc1.42001e", bitrate = 700_000),
    )

    @Test
    fun picksTheHighestPreferredCodecAtOrBelowTheLimit() {
        val choice = VideoStreamChoice.pick(candidates, maxHeight = 1080, preferredCodecPrefix = "avc1")
        assertEquals(VideoCandidate(height = 1080, codec = "avc1.640028", bitrate = 5_000_000), choice)
    }

    @Test
    fun fallsBackToAnyCodecWhenThePreferredOneIsMissing() {
        val onlyVp9 = candidates.filter { it.codec == "vp9" }
        val choice = VideoStreamChoice.pick(onlyVp9, maxHeight = 1080, preferredCodecPrefix = "avc1")
        assertEquals(VideoCandidate(height = 1080, codec = "vp9", bitrate = 4_000_000), choice)
    }

    @Test
    fun takesTheLowestStreamWhenEverythingIsAboveTheLimit() {
        val choice = VideoStreamChoice.pick(candidates, maxHeight = 240, preferredCodecPrefix = "avc1")
        assertEquals(VideoCandidate(height = 360, codec = "avc1.42001e", bitrate = 700_000), choice)
    }

    @Test
    fun emptyListHasNoChoice() {
        assertEquals(null, VideoStreamChoice.pick(emptyList(), maxHeight = 1080, preferredCodecPrefix = "avc1"))
    }
}
