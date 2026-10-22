package org.tubepipe.tv.subscriptions

import kotlin.time.Duration.Companion.seconds
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
import org.tubepipe.core.database.SubscriptionEntity
import org.tubepipe.tv.testing.FakeContentRepository
import org.tubepipe.tv.testing.MainDispatcherRule
import org.tubepipe.tv.testing.TestDatabaseRule
import org.tubepipe.tv.testing.videos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubscriptionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val databaseRule = TestDatabaseRule()

    private val content = FakeContentRepository().apply { feed = videos("new", 15) }

    private fun viewModel() = SubscriptionsViewModel(content, databaseRule.database.subscriptionDao())

    @Test
    fun noSubscriptionsMeansAnEmptyStateWithoutNetwork() = runTest(timeout = 30.seconds) {
        val state = viewModel().state.first { !it.isLoading }

        assertTrue(state.channels.isEmpty())
        assertTrue(state.feed.isEmpty())
        assertNull(content.feedRequest)
    }

    @Test
    fun channelsAndTheirNewestVideosAreShown() = runTest(timeout = 30.seconds) {
        val subscriptions = databaseRule.database.subscriptionDao()
        subscriptions.upsert(SubscriptionEntity("https://www.youtube.com/channel/B", "Beta", null, 1))
        subscriptions.upsert(SubscriptionEntity("https://www.youtube.com/channel/A", "Alpha", null, 1))

        val state = viewModel().state.first { !it.isLoading && it.feed.isNotEmpty() }

        assertEquals(listOf("Alpha", "Beta"), state.channels.map { it.name })
        assertEquals(videos("new", 15), state.feed)
        assertEquals(
            setOf("https://www.youtube.com/channel/A", "https://www.youtube.com/channel/B"),
            content.feedRequest?.toSet(),
        )
    }

    @Test
    fun feedFailureShowsAnErrorAndRetryRecovers() = runTest(timeout = 30.seconds) {
        databaseRule.database.subscriptionDao().upsert(
            SubscriptionEntity("https://www.youtube.com/channel/A", "Alpha", null, 1),
        )
        content.failures += "feed"
        val viewModel = viewModel()
        assertTrue(viewModel.state.first { !it.isLoading && it.channels.isNotEmpty() }.hasError)

        content.failures.clear()
        viewModel.retry()
        assertEquals(15, viewModel.state.first { it.feed.isNotEmpty() }.feed.size)
    }
}
