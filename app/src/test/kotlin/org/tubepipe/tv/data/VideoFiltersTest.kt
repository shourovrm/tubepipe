package org.tubepipe.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tubepipe.tv.testing.testSettings
import org.tubepipe.tv.testing.video

class VideoFiltersTest {
    private val blocked = setOf("motu patlu", "মোটু পাতলু")

    private fun titled(title: String, channel: String = "Channel") = video("id", channel = channel).copy(title = title)

    @Test
    fun oneEntryCatchesCaseSpacingAndPunctuationVariants() {
        assertEquals("motu patlu", blocked.blockedWordIn(titled("MOTU PATLU full episode")))
        assertEquals("motu patlu", blocked.blockedWordIn(titled("New MotuPatlu | Ep 12")))
        assertEquals("motu patlu", blocked.blockedWordIn(titled("Motu-Patlu ki jodi")))
        assertEquals("motu patlu", blocked.blockedWordIn(titled("Motú Patlú")))
    }

    @Test
    fun anotherScriptNeedsItsOwnEntry() {
        assertEquals("মোটু পাতলু", blocked.blockedWordIn(titled("মোটু পাতলু নতুন পর্ব")))
        assertNull(setOf("motu patlu").blockedWordIn(titled("মোটু পাতলু নতুন পর্ব")))
    }

    @Test
    fun channelNameCountsToo() {
        assertEquals("motu patlu", blocked.blockedWordIn(titled("Episode 3", channel = "Motu Patlu Official")))
    }

    @Test
    fun unrelatedTitlesAndEmptyWordsMatchNothing() {
        assertNull(blocked.blockedWordIn(titled("Masha and the Bear")))
        assertNull(setOf("", "  ", "- -").blockedWordIn(titled("Anything")))
        assertNull(emptySet<String>().blockedWordIn(titled("Motu Patlu")))
    }

    @Test
    fun hidesCombinesTheSwitchesAndTheWords() {
        val settings = testSettings().value.copy(blockedWords = blocked, hideShorts = true)
        assertEquals(true, settings.hides(titled("Motu Patlu")))
        assertEquals(true, settings.hides(titled("A short").copy(isShort = true)))
        assertEquals(false, settings.hides(titled("A normal video")))
    }
}
