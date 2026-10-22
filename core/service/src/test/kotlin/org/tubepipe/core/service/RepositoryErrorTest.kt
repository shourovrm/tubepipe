package org.tubepipe.core.service

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Callers outside this module cannot see ExtractionException, so every failure arrives as IOException. */
class RepositoryErrorTest {
    @Test
    fun extractorFailureArrivesAsIOException() {
        ExtractorInitializer.initialize(languageCode = "en", countryCode = "US")
        val repository = YoutubeContentRepository()

        // A URL no YouTube link handler accepts fails inside the extractor before any network call.
        val error = assertThrows(IOException::class.java) {
            runBlocking { repository.videoDetails("https://example.com/not-a-video") }
        }
        assertTrue(error.message.orEmpty().contains("https://example.com/not-a-video"))
    }
}
