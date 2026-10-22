package org.tubepipe.tv.search

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tubepipe.tv.testing.FakeContentRepository
import org.tubepipe.tv.testing.MainDispatcherRule
import org.tubepipe.tv.testing.TestDatabaseRule
import org.tubepipe.tv.testing.videos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val databaseRule = TestDatabaseRule()

    private val content = FakeContentRepository().apply {
        searchFirstPages["cats"] = videos("cat", 20)
        searchSecondPages["cats"] = videos("morecat", 20)
        suggestions["cat"] = listOf("cats", "cat videos")
    }
    private var nowMillis = 1_000L

    private fun viewModel() = SearchViewModel(content, databaseRule.database.searchDao(), clock = { nowMillis })

    @Test
    fun blankQueryShowsRecentSearchesNewestFirst() = runTest(timeout = 30.seconds) {
        val searches = databaseRule.database.searchDao()
        searches.recordSearch("dogs", searchedAtMillis = 1)
        searches.recordSearch("birds", searchedAtMillis = 2)

        val state = viewModel().state.first { it.recentSearches.isNotEmpty() }

        assertEquals(listOf("birds", "dogs"), state.recentSearches)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun suggestionsAreFetchedOnlyAfterTypingPauses() = runTest(mainDispatcherRule.dispatcher, timeout = 30.seconds) {
        // Sharing the Main test dispatcher lets advanceTimeBy drive the view model's debounce.
        val viewModel = viewModel()
        viewModel.onQueryChange("ca")
        advanceTimeBy(100)
        viewModel.onQueryChange("cat")
        advanceTimeBy(SearchViewModel.SUGGESTION_DELAY_MILLIS + 50)
        runCurrent()

        assertEquals(listOf("cat"), content.suggestionRequests)
        assertEquals(listOf("cats", "cat videos"), viewModel.state.value.suggestions)
    }

    @Test
    fun submitShowsResultsAndRemembersTheQuery() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()

        viewModel.submit("cats")

        val state = viewModel.state.first { !it.isLoading && it.results.isNotEmpty() }
        assertEquals("cats", state.submittedQuery)
        assertEquals(videos("cat", 20), state.results)
        assertTrue(state.canLoadMore)
        assertEquals("cats", viewModel.state.first { it.recentSearches.isNotEmpty() }.recentSearches.first())
    }

    @Test
    fun loadMoreAppendsTheNextPageOnce() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.submit("cats")
        viewModel.state.first { !it.isLoading && it.results.isNotEmpty() }

        viewModel.loadMore()

        val state = viewModel.state.first { it.results.size == 40 }
        assertEquals(videos("cat", 20) + videos("morecat", 20), state.results)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun blankSubmitDoesNothing() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.submit("   ")
        assertEquals(null, viewModel.state.value.submittedQuery)
    }

    @Test
    fun failureShowsAnErrorAndRetryRepeatsTheSearch() = runTest(timeout = 30.seconds) {
        content.failures += "search"
        val viewModel = viewModel()
        viewModel.submit("cats")
        assertTrue(viewModel.state.first { !it.isLoading && it.submittedQuery == "cats" }.hasError)

        content.failures.clear()
        viewModel.retry()
        assertEquals(20, viewModel.state.first { !it.isLoading && it.results.isNotEmpty() }.results.size)
    }

    @Test
    fun savingTheSearchAddsItToSavedSearches() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.submit("cats")
        viewModel.state.first { !it.isLoading && it.results.isNotEmpty() }

        viewModel.saveCurrentSearch()

        val saved = databaseRule.database.searchDao().observeSavedSearches().first { it.isNotEmpty() }
        assertEquals(listOf("cats"), saved.map { it.query })
    }
}
