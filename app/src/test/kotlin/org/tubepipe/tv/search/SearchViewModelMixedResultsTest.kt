package org.tubepipe.tv.search

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tubepipe.core.service.ChannelSummary
import org.tubepipe.core.service.PlaylistSummary
import org.tubepipe.core.service.SearchResult
import org.tubepipe.tv.testing.FakeContentRepository
import org.tubepipe.tv.testing.MainDispatcherRule
import org.tubepipe.tv.testing.TestDatabaseRule
import org.tubepipe.tv.testing.testSettings
import org.tubepipe.tv.testing.videos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchViewModelMixedResultsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val databaseRule = TestDatabaseRule()

    private val channel = SearchResult.Channel(
        ChannelSummary(
            url = "https://www.youtube.com/channel/cats",
            name = "Cats",
            avatarUrl = null,
            subscriberCount = 9L,
        ),
    )
    private val playlist = SearchResult.Playlist(
        PlaylistSummary(
            url = "https://www.youtube.com/playlist?list=PLcats",
            title = "Cat mix",
            thumbnailUrl = null,
            uploaderName = "Cats",
            videoCount = 12L,
        ),
    )
    private val content = FakeContentRepository().apply {
        searchFirstPages["cats"] = videos("cat", 2)
        searchExtraResults["cats"] = listOf(channel, playlist)
        searchFirstPages["dogs"] = videos("dog", 3)
    }

    private fun viewModel() = SearchViewModel(
        content = content,
        searchDao = databaseRule.database.searchDao(),
        settings = testSettings(),
    )

    @Test
    fun channelsAndPlaylistsStayInTheResults() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()

        viewModel.submit("cats")

        val state = viewModel.state.first { !it.isLoading && it.items.isNotEmpty() }
        assertEquals(4, state.items.size)
        assertTrue(channel in state.items)
        assertTrue(playlist in state.items)
        assertEquals(videos("cat", 2), state.results)
    }

    @Test
    fun aSecondAndThirdSearchReplaceTheEarlierResults() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.submit("cats")
        viewModel.state.first { !it.isLoading && it.items.size == 4 }

        viewModel.onQueryChange("dogs")
        viewModel.submit("dogs")
        val dogState = viewModel.state.first { !it.isLoading && it.submittedQuery == "dogs" && it.items.isNotEmpty() }
        assertEquals(videos("dog", 3), dogState.results)
        assertEquals(3, dogState.items.size)

        viewModel.submit("cats")
        val catState = viewModel.state.first { !it.isLoading && it.submittedQuery == "cats" && it.items.isNotEmpty() }
        assertEquals(4, catState.items.size)
    }
}
