package org.tubepipe.core.service

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class VideoItemMapperTest {
    private fun extractorItem(streamType: StreamType): StreamInfoItem {
        val item = StreamInfoItem(0, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Video title", streamType)
        item.uploaderName = "Channel name"
        item.uploaderUrl = "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"
        item.thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        item.duration = 212
        item.viewCount = 1_000_000
        item.textualUploadDate = "2 days ago"
        return item
    }

    @Test
    fun videoFieldsAreCopied() {
        val video = extractorItem(StreamType.VIDEO_STREAM).toVideoItem()

        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", video.url)
        assertEquals("Video title", video.title)
        assertEquals("Channel name", video.channelName)
        assertEquals("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw", video.channelUrl)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", video.thumbnailUrl)
        assertEquals(212L, video.durationSeconds)
        assertEquals(1_000_000L, video.viewCount)
        assertEquals("2 days ago", video.uploadedText)
        assertFalse(video.isLive)
    }

    @Test
    fun liveStreamHasNoDuration() {
        val item = extractorItem(StreamType.LIVE_STREAM)
        item.duration = -1

        val video = item.toVideoItem()

        assertTrue(video.isLive)
        assertNull(video.durationSeconds)
    }

    @Test
    fun unknownCountsBecomeNull() {
        val item = extractorItem(StreamType.VIDEO_STREAM)
        item.viewCount = -1
        item.textualUploadDate = null

        val video = item.toVideoItem()

        assertNull(video.viewCount)
        assertNull(video.uploadedText)
    }

    @Test
    fun shortsMembersOnlyAndUpcomingAreFlagged() {
        val item = extractorItem(StreamType.VIDEO_STREAM)
        item.isShortFormContent = true
        item.setRequiresMembership(true)

        val video = item.toVideoItem()

        assertTrue(video.isShort)
        assertTrue(video.isMembersOnly)
        assertFalse(video.isUpcoming)
    }

    @Test
    fun aFutureUploadDateMeansUpcoming() {
        val item = extractorItem(StreamType.VIDEO_STREAM)
        item.uploadDate = DateWrapper(OffsetDateTime.now().plusDays(2))

        assertTrue(item.toVideoItem().isUpcoming)
    }
}
