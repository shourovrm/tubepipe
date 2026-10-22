package org.tubepipe.core.service

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Hits YouTube for real. Skipped unless run with -Ptubepipe.live=true, because a network or
 * YouTube outage must not fail the normal unit test run.
 */
class LiveYoutubeSmokeTest {
    private val repository = YoutubeContentRepository()

    @Before
    fun startExtractor() {
        assumeTrue(System.getProperty("tubepipe.live") == "true")
        ExtractorInitializer.initialize(languageCode = "en", countryCode = "US")
    }

    @Test
    fun searchReturnsVideos() = runTest(timeout = 60.seconds) {
        val page = repository.search("never gonna give you up")
        assertTrue("search returned ${page.items.size} videos", page.items.size >= 5)
        assertTrue(page.items.all { it.url.isNotBlank() && it.title.isNotBlank() })

        val nextPage = repository.searchMore("never gonna give you up", page.next!!)
        assertTrue("second page returned ${nextPage.items.size} videos", nextPage.items.isNotEmpty())
    }

    @Test
    fun searchSuggestionsAreReturned() = runTest(timeout = 60.seconds) {
        assertTrue(repository.searchSuggestions("never gonna").isNotEmpty())
    }

    @Test
    fun everyTrendingTabReturnsVideosInTheUnitedStates() = runTest(timeout = 120.seconds) {
        ExtractorInitializer.setContentCountry("US")
        for (kind in TrendingCatalog.kindsFor("US")) {
            val videos = repository.trending(kind)
            assertTrue("$kind returned ${videos.size} videos", videos.size >= 10)
        }
    }

    @Test
    fun trendingTabsShownInBangladeshReturnVideos() = runTest(timeout = 120.seconds) {
        ExtractorInitializer.setContentCountry("BD")
        for (kind in TrendingCatalog.kindsFor("BD")) {
            val videos = repository.trending(kind)
            assertTrue("$kind returned ${videos.size} videos", videos.size >= 10)
        }
    }

    @Test
    fun videoDetailsIncludeRelatedVideos() = runTest(timeout = 60.seconds) {
        val details = repository.videoDetails("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(details.video.title.isNotBlank())
        assertTrue(details.video.channelName.isNotBlank())
        assertTrue("related has ${details.related.size} videos", details.related.isNotEmpty())
    }

    @Test
    fun mixReturnsVideosForASeedVideo() = runTest(timeout = 60.seconds) {
        val videos = repository.mix("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue("mix has ${videos.size} videos", videos.size >= 5)
    }

    @Test
    fun channelPageHasHeaderAndVideos() = runTest(timeout = 60.seconds) {
        val channel = repository.channel("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw")
        assertTrue(channel.channel.name.isNotBlank())
        assertTrue("channel has ${channel.videos.items.size} videos", channel.videos.items.isNotEmpty())
    }

    @Test
    fun subscriptionFeedReturnsRecentVideos() = runTest(timeout = 60.seconds) {
        val videos = repository.latestFromChannels(
            listOf("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"),
        )
        assertTrue("feed has ${videos.size} videos", videos.isNotEmpty())
    }
}
