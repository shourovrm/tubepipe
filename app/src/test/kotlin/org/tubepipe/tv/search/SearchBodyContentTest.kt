package org.tubepipe.tv.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tubepipe.core.service.SearchResult
import org.tubepipe.tv.testing.videos

class SearchBodyContentTest {
    private val earlierResults = videos("cat", 3).map { video -> SearchResult.Video(video) }

    // The state after searching "cats": results on screen, the query still in the box.
    private val afterEarlierSearch = SearchState(
        query = "cats",
        submittedQuery = "cats",
        items = earlierResults,
        recentSearches = listOf("cats"),
        submissionCount = 1,
    )

    @Test
    fun submittedQueryWithItemsShowsResults() {
        assertEquals(SearchBodyContent.Results, bodyContentFor(afterEarlierSearch))
    }

    @Test
    fun emptyBoxAfterEarlierSearchShowsRecentSearches() {
        val cleared = afterEarlierSearch.copy(query = "")

        assertEquals(SearchBodyContent.RecentSearches, bodyContentFor(cleared))
    }

    // The Mi Box bug: clear the box, type one letter, and before suggestions arrive the body flipped back to the
    // earlier results, which hid the keyboard and moved focus to the grid.
    @Test
    fun firstLetterOfNewQueryBeforeSuggestionsIsTypingNotResults() {
        val firstLetter = afterEarlierSearch.copy(query = "d")

        val bodyContent = bodyContentFor(firstLetter)

        assertEquals(SearchBodyContent.Typing, bodyContent)
        assertFalse(bodyContent.hasFocusableItems)
    }

    @Test
    fun newQueryWithSuggestionsShowsSuggestions() {
        val withSuggestions = afterEarlierSearch.copy(query = "d", suggestions = listOf("dogs"))

        assertEquals(SearchBodyContent.Suggestions, bodyContentFor(withSuggestions))
    }

    @Test
    fun firstLetterWithNoEarlierSearchIsTyping() {
        val fresh = SearchState(query = "d")

        assertEquals(SearchBodyContent.Typing, bodyContentFor(fresh))
    }

    @Test
    fun resultsOfANewSubmissionTakeFocus() {
        val shouldMove = shouldMoveFocusToResults(
            bodyContent = SearchBodyContent.Results,
            submissionCount = 2,
            lastFocusedSubmissionCount = 1,
        )

        assertTrue(shouldMove)
    }

    // Typing a query back to the one already submitted shows its results again, but the viewer is still typing,
    // so the keyboard must stay open.
    @Test
    fun resultsShownAgainForTheSameSubmissionDoNotTakeFocus() {
        val retyped = afterEarlierSearch.copy(query = "cats")
        val shouldMove = shouldMoveFocusToResults(
            bodyContent = bodyContentFor(retyped),
            submissionCount = retyped.submissionCount,
            lastFocusedSubmissionCount = 1,
        )

        assertFalse(shouldMove)
    }

    @Test
    fun bodyWithoutResultsNeverTakesFocus() {
        val shouldMove = shouldMoveFocusToResults(
            bodyContent = SearchBodyContent.Loading,
            submissionCount = 2,
            lastFocusedSubmissionCount = 1,
        )

        assertFalse(shouldMove)
    }
}
