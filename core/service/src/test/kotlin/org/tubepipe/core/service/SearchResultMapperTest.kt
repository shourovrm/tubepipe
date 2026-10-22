package org.tubepipe.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class SearchResultMapperTest {
    private fun channelItem(): ChannelInfoItem {
        val item = ChannelInfoItem(0, "https://www.youtube.com/channel/UCabc", "Channel name")
        item.thumbnailUrl = "https://yt3.ggpht.com/avatar.jpg"
        item.subscriberCount = 1_200_000
        return item
    }

    private fun playlistItem(): PlaylistInfoItem {
        val item = PlaylistInfoItem(0, "https://www.youtube.com/playlist?list=PL123", "Playlist title")
        item.thumbnailUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg"
        item.uploaderName = "Uploader"
        item.streamCount = 42
        return item
    }

    private fun videoItem() =
        StreamInfoItem(0, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Video", StreamType.VIDEO_STREAM)

    @Test
    fun channelFieldsAreCopied() {
        val channel = channelItem().toChannelSummary()

        assertEquals("https://www.youtube.com/channel/UCabc", channel.url)
        assertEquals("Channel name", channel.name)
        assertEquals("https://yt3.ggpht.com/avatar.jpg", channel.avatarUrl)
        assertEquals(1_200_000L, channel.subscriberCount)
    }

    @Test
    fun hiddenSubscriberCountIsNull() {
        val item = channelItem()
        item.subscriberCount = -1

        assertNull(item.toChannelSummary().subscriberCount)
    }

    @Test
    fun playlistFieldsAreCopied() {
        val playlist = playlistItem().toPlaylistSummary()

        assertEquals("https://www.youtube.com/playlist?list=PL123", playlist.url)
        assertEquals("Playlist title", playlist.title)
        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", playlist.thumbnailUrl)
        assertEquals("Uploader", playlist.uploaderName)
        assertEquals(42L, playlist.videoCount)
    }

    @Test
    fun unknownPlaylistSizeIsNull() {
        val item = playlistItem()
        item.streamCount = -1

        assertNull(item.toPlaylistSummary().videoCount)
    }

    @Test
    fun eachInfoItemKindMapsToItsSearchResult() {
        assertTrue(videoItem().toSearchResultOrNull() is SearchResult.Video)
        assertTrue(channelItem().toSearchResultOrNull() is SearchResult.Channel)
        assertTrue(playlistItem().toSearchResultOrNull() is SearchResult.Playlist)
    }

    @Test
    fun videoItemsKeepsOnlyVideosInOrder() {
        val results = listOf(
            SearchResult.Channel(channelItem().toChannelSummary()),
            SearchResult.Video(videoItem().toVideoItem()),
            SearchResult.Playlist(playlistItem().toPlaylistSummary()),
        )

        assertEquals(listOf("Video"), results.videoItems().map { it.title })
    }
}
