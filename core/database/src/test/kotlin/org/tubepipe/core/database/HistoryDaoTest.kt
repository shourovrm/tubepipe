package org.tubepipe.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryDaoTest {
    @get:Rule
    val databaseRule = DatabaseTestRule()
    private val history get() = databaseRule.database.historyDao()

    private fun entry(url: String, watchedAtMillis: Long, positionMillis: Long = 0L) = HistoryEntity(
        url = url,
        title = "Title $url",
        channelName = "Channel",
        thumbnailUrl = null,
        durationSeconds = 600L,
        lastWatchedAtMillis = watchedAtMillis,
        positionMillis = positionMillis,
    )

    @Test
    fun recentHistoryIsNewestFirstAndLimited() = runTest {
        history.upsert(entry("a", watchedAtMillis = 1))
        history.upsert(entry("b", watchedAtMillis = 3))
        history.upsert(entry("c", watchedAtMillis = 2))

        assertEquals(listOf("b", "c"), history.observeRecent(limit = 2).first().map { it.url })
    }

    @Test
    fun savedPositionIsReadBackAndMovesTheEntryToTheTop() = runTest {
        history.upsert(entry("a", watchedAtMillis = 1))
        history.upsert(entry("b", watchedAtMillis = 2))

        history.savePosition(url = "a", positionMillis = 42_000L, watchedAtMillis = 5)

        assertEquals(42_000L, history.positionOf("a"))
        assertEquals("a", history.observeRecent(limit = 10).first().first().url)
    }

    @Test
    fun unknownVideoHasNoPosition() = runTest {
        assertNull(history.positionOf("never watched"))
    }

    @Test
    fun clearRemovesEverything() = runTest {
        history.upsert(entry("a", watchedAtMillis = 1))
        history.clear()
        assertTrue(history.observeRecent(limit = 10).first().isEmpty())
    }
}
