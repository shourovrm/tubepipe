package org.tubepipe.tv.channel

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
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
class ChannelViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val databaseRule = TestDatabaseRule()

    private val channelUrl = "https://www.youtube.com/channel/UCa"
    private val content = FakeContentRepository().apply {
        addChannel(channelUrl, name = "Channel A", firstPage = videos("a", 30))
        channelSecondPages[channelUrl] = videos("older", 30)
    }

    private fun viewModel() = ChannelViewModel(
        channelUrl = channelUrl,
        content = content,
        subscriptions = databaseRule.database.subscriptionDao(),
        clock = { 5_000L },
    )

    @Test
    fun headerAndFirstPageLoad() = runTest(timeout = 30.seconds) {
        val state = viewModel().state.first { !it.isLoading && it.channel != null }

        assertEquals("Channel A", state.channel?.name)
        assertEquals(videos("a", 30), state.videos)
        assertTrue(state.canLoadMore)
        assertFalse(state.isSubscribed)
    }

    @Test
    fun subscribeAndUnsubscribe() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.state.first { !it.isLoading && it.channel != null }

        viewModel.toggleSubscription()
        assertTrue(viewModel.state.first { it.isSubscribed }.isSubscribed)
        val stored = databaseRule.database.subscriptionDao().observeAll().first { it.isNotEmpty() }.single()
        assertEquals(channelUrl, stored.url)
        assertEquals("Channel A", stored.name)
        assertEquals(5_000L, stored.subscribedAtMillis)

        viewModel.toggleSubscription()
        assertFalse(viewModel.state.first { !it.isSubscribed }.isSubscribed)
    }

    @Test
    fun loadMoreAppendsOlderVideos() = runTest(timeout = 30.seconds) {
        val viewModel = viewModel()
        viewModel.state.first { !it.isLoading && it.channel != null }

        viewModel.loadMore()

        val state = viewModel.state.first { it.videos.size == 60 }
        assertFalse(state.canLoadMore)
    }

    @Test
    fun failureShowsAnErrorAndRetryRecovers() = runTest(timeout = 30.seconds) {
        content.failures += "channel:$channelUrl"
        val viewModel = viewModel()
        assertTrue(viewModel.state.first { !it.isLoading }.hasError)

        content.failures.clear()
        viewModel.retry()
        assertEquals("Channel A", viewModel.state.first { it.channel != null }.channel?.name)
    }
}
