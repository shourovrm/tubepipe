package org.tubepipe.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PipePipe and NewPipe export subscriptions as JSON; Tubepipe imports the YouTube ones (service_id 0). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewPipeSubscriptionsImportTest {
    private val exportJson = """
        {
          "app_version": "4.7.2",
          "app_version_int": 1007,
          "subscriptions": [
            {"service_id": 0, "url": "https://www.youtube.com/channel/UCa", "name": "Channel A"},
            {"service_id": 1, "url": "https://soundcloud.com/someone", "name": "SoundCloud artist"},
            {"service_id": 0, "url": "https://www.youtube.com/channel/UCb", "name": "Channel B"}
          ]
        }
    """.trimIndent()

    @Test
    fun youtubeSubscriptionsAreImportedAndOthersSkipped() {
        val imported = NewPipeSubscriptionsImport.parse(exportJson)

        assertEquals(
            listOf(
                ImportedSubscription("https://www.youtube.com/channel/UCa", "Channel A"),
                ImportedSubscription("https://www.youtube.com/channel/UCb", "Channel B"),
            ),
            imported,
        )
    }

    @Test
    fun malformedFileFailsWithAClearError() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NewPipeSubscriptionsImport.parse("""{"no_subscriptions_here": true}""")
        }
        assertEquals("Not a NewPipe or PipePipe subscriptions export: missing \"subscriptions\"", error.message)
    }
}
