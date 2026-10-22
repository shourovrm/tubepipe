package org.tubepipe.tv.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tubepipe.core.service.TrendingKind
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.service.videoItems
import org.tubepipe.tv.testing.FakeContentRepository
import org.tubepipe.tv.testing.testSettings
import org.tubepipe.tv.testing.video

class FilteringContentRepositoryTest {
    private val normal = video("normal")
    private val short = video("short").copy(isShort = true)
    private val upcoming = video("upcoming").copy(isUpcoming = true)
    private val membersOnly = video("members").copy(isMembersOnly = true)
    private val mixed = listOf(normal, short, upcoming, membersOnly)

    private val content = FakeContentRepository().apply {
        searchFirstPages["q"] = mixed
        trendingVideos[TrendingKind.Gaming] = mixed
        mixes[normal.url] = mixed
        feed = mixed
        addDetails(normal, related = mixed)
        addChannel("https://www.youtube.com/channel/C", "C", firstPage = mixed)
    }

    private fun titles(videos: List<VideoItem>) = videos.map { it.title }

    @Test
    fun everyFilterOnLeavesOnlyNormalVideosEverywhere() = runTest {
        val settings = testSettings().apply {
            value =
                value.copy(hideShorts = true, hideUpcoming = true, hidePaid = true)
        }
        val filtering = FilteringContentRepository(content, settings)

        assertEquals(titles(listOf(normal)), titles(filtering.search("q").items.videoItems()))
        assertEquals(titles(listOf(normal)), titles(filtering.trending(TrendingKind.Gaming)))
        assertEquals(titles(listOf(normal)), titles(filtering.mix(normal.url)))
        assertEquals(titles(listOf(normal)), titles(filtering.latestFromChannels(listOf("x"))))
        assertEquals(titles(listOf(normal)), titles(filtering.videoDetails(normal.url).related))
        assertEquals(
            titles(listOf(normal)),
            titles(filtering.channel("https://www.youtube.com/channel/C").videos.items),
        )
    }

    @Test
    fun eachFilterHidesOnlyItsKind() = runTest {
        val settings = testSettings().apply {
            value =
                value.copy(hideShorts = true, hideUpcoming = false, hidePaid = false)
        }

        assertEquals(
            titles(listOf(normal, upcoming, membersOnly)),
            titles(FilteringContentRepository(content, settings).search("q").items.videoItems()),
        )
    }

    @Test
    fun filtersOffShowEverything() = runTest {
        val settings = testSettings().apply {
            value =
                value.copy(hideShorts = false, hideUpcoming = false, hidePaid = false)
        }

        assertEquals(
            titles(mixed),
            titles(FilteringContentRepository(content, settings).search("q").items.videoItems()),
        )
    }

    @Test
    fun theVideoBeingOpenedIsNeverFiltered() = runTest {
        val settings = testSettings().apply { value = value.copy(hideShorts = true) }
        content.addDetails(short, related = emptyList())

        assertEquals(short.title, FilteringContentRepository(content, settings).videoDetails(short.url).video.title)
    }
}
