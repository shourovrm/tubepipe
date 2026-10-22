package org.tubepipe.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchDaoTest {
    @get:Rule
    val databaseRule = DatabaseTestRule()
    private val searches get() = databaseRule.database.searchDao()

    @Test
    fun searchHistoryIsNewestFirstWithoutDuplicates() = runTest {
        searches.recordSearch(query = "cats", searchedAtMillis = 1)
        searches.recordSearch(query = "dogs", searchedAtMillis = 2)
        searches.recordSearch(query = "cats", searchedAtMillis = 3)

        assertEquals(listOf("cats", "dogs"), searches.observeRecentSearches(limit = 10).first().map { it.query })
    }

    @Test
    fun onlyOneSavedSearchIsPinnedAtATime() = runTest {
        searches.saveSearch("cats")
        searches.saveSearch("dogs")

        searches.pin("cats")
        assertEquals("cats", searches.observePinned().first()?.query)

        searches.pin("dogs")
        assertEquals("dogs", searches.observePinned().first()?.query)
        assertEquals(1, searches.observeSavedSearches().first().count { it.pinned })
    }

    @Test
    fun unpinAndDeleteSavedSearch() = runTest {
        searches.saveSearch("cats")
        searches.pin("cats")

        searches.unpinAll()
        assertNull(searches.observePinned().first())

        searches.deleteSavedSearch("cats")
        assertEquals(emptyList<String>(), searches.observeSavedSearches().first().map { it.query })
    }
}
