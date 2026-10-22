package org.tubepipe.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YoutubeVideoIdTest {
    @Test
    fun idIsReadFromTheUsualUrlShapes() {
        assertEquals("dQw4w9WgXcQ", YoutubeVideoId.from("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(
            "dQw4w9WgXcQ",
            YoutubeVideoId.from("https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=10"),
        )
        assertEquals("dQw4w9WgXcQ", YoutubeVideoId.from("https://youtu.be/dQw4w9WgXcQ?t=5"))
        assertEquals("dQw4w9WgXcQ", YoutubeVideoId.from("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun otherUrlsHaveNoId() {
        assertNull(YoutubeVideoId.from("https://www.youtube.com/channel/UCa"))
    }
}
