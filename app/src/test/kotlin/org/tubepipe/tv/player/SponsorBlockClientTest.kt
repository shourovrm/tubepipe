package org.tubepipe.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorBlockClientTest {
    @Test
    fun skipSegmentsResponseIsParsedToMilliseconds() {
        // Shape of https://sponsor.ajay.app/api/skipSegments?videoID=...&categories=[...]
        val json = """
            [
              {"category": "sponsor", "actionType": "skip", "segment": [12.5, 30.0], "UUID": "a", "videoDuration": 600},
              {"category": "intro", "actionType": "skip", "segment": [0, 4.25], "UUID": "b", "videoDuration": 600},
              {"category": "sponsor", "actionType": "mute", "segment": [100, 110], "UUID": "c", "videoDuration": 600}
            ]
        """.trimIndent()

        val segments = SponsorBlockClient.parse(json)

        assertEquals(
            listOf(
                SkipSegment(startMillis = 0, endMillis = 4_250, category = "intro"),
                SkipSegment(startMillis = 12_500, endMillis = 30_000, category = "sponsor"),
            ),
            segments,
        )
    }

    @Test
    fun emptyArrayMeansNoSegments() {
        assertEquals(emptyList<SkipSegment>(), SponsorBlockClient.parse("[]"))
    }
}
