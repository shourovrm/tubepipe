package org.tubepipe.core.service

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.schabi.newpipe.extractor.localization.ContentCountry

class YoutubeNewsFeedTest {
    private val requestedCountries = mutableListOf<String>()

    private fun fakeFeed(itemsByCountry: Map<String, List<String>>): (ContentCountry) -> List<String> = { country ->
        requestedCountries += country.countryCode
        itemsByCountry[country.countryCode].orEmpty()
    }

    @Test
    fun countryWithNewsKeepsItsOwnFeed() {
        val feed = fakeFeed(mapOf("BD" to listOf("bd-news"), "US" to listOf("us-news")))

        val items = YoutubeNewsFeed.withUsFallback(ContentCountry("BD"), feed)

        assertEquals(listOf("bd-news"), items)
        assertEquals(listOf("BD"), requestedCountries)
    }

    // YouTube answers 200 with no videos where its News channel is not offered (checked for CN, KP, IR, AF, NP, MM).
    @Test
    fun countryWithoutNewsFallsBackToUs() {
        val feed = fakeFeed(mapOf("CN" to emptyList(), "US" to listOf("us-news")))

        val items = YoutubeNewsFeed.withUsFallback(ContentCountry("CN"), feed)

        assertEquals(listOf("us-news"), items)
        assertEquals(listOf("CN", "US"), requestedCountries)
    }

    @Test
    fun emptyUsFeedIsNotRequestedTwice() {
        val feed = fakeFeed(emptyMap())

        val items = YoutubeNewsFeed.withUsFallback(ContentCountry("US"), feed)

        assertEquals(emptyList<String>(), items)
        assertEquals(listOf("US"), requestedCountries)
    }

    // A network failure would fail for the US as well, so it reaches the tab's error view instead of a second request.
    @Test
    fun networkFailureIsNotRetriedWithUs() {
        val failingFeed: (ContentCountry) -> List<String> = { country ->
            requestedCountries += country.countryCode
            throw IOException("offline")
        }

        assertThrows(IOException::class.java) {
            YoutubeNewsFeed.withUsFallback(ContentCountry("CN"), failingFeed)
        }
        assertEquals(listOf("CN"), requestedCountries)
    }
}
