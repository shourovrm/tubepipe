package org.tubepipe.core.service.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request

class RestrictedModeTest {
    @After
    fun turnOff() {
        DownloaderImpl.setRestrictedMode(false)
    }

    private fun get(url: String, headers: Map<String, List<String>> = emptyMap()): okhttp3.Request =
        DownloaderImpl.buildOkHttpRequest(Request.newBuilder().get(url).headers(headers).build())

    @Test
    fun youtubeRequestsCarryTheRestrictedModeCookieWhenOn() {
        DownloaderImpl.setRestrictedMode(true)

        assertEquals("PREF=f2=8000000", get("https://www.youtube.com/results?search_query=a").header("Cookie"))
    }

    @Test
    fun anExistingCookieIsKept() {
        DownloaderImpl.setRestrictedMode(true)

        val request = get("https://m.youtube.com/watch?v=x", mapOf("Cookie" to listOf("SOCS=CAE")))

        assertEquals("SOCS=CAE; PREF=f2=8000000", request.header("Cookie"))
    }

    @Test
    fun otherHostsAreLeftAlone() {
        DownloaderImpl.setRestrictedMode(true)

        assertNull(get("https://sponsor.ajay.app/api/skipSegments").header("Cookie"))
        assertNull(get("https://notyoutube.com/").header("Cookie"))
    }

    @Test
    fun nothingIsAddedWhenOff() {
        assertNull(get("https://www.youtube.com/").header("Cookie"))
    }
}
