package org.tubepipe.core.database

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubscriptionDaoTest {
    @get:Rule
    val databaseRule = DatabaseTestRule()
    private val subscriptions get() = databaseRule.database.subscriptionDao()

    private fun subscription(url: String, name: String) =
        SubscriptionEntity(url = url, name = name, avatarUrl = null, subscribedAtMillis = 1_000L)

    @Test
    fun subscriptionsAreListedByName() = runTest {
        subscriptions.upsert(subscription("https://www.youtube.com/channel/B", "Beta"))
        subscriptions.upsert(subscription("https://www.youtube.com/channel/A", "alpha"))

        val names = subscriptions.observeAll().first().map { it.name }

        assertEquals(listOf("alpha", "Beta"), names)
    }

    @Test
    fun subscribeAndUnsubscribeUpdateTheFlag() = runTest {
        val url = "https://www.youtube.com/channel/A"
        assertFalse(subscriptions.observeIsSubscribed(url).first())

        subscriptions.upsert(subscription(url, "A"))
        assertTrue(subscriptions.observeIsSubscribed(url).first())

        subscriptions.delete(url)
        assertFalse(subscriptions.observeIsSubscribed(url).first())
    }

    @Test
    fun upsertReplacesTheSameChannel() = runTest {
        val url = "https://www.youtube.com/channel/A"
        subscriptions.upsert(subscription(url, "Old name"))
        subscriptions.upsert(subscription(url, "New name"))

        assertEquals(listOf("New name"), subscriptions.observeAll().first().map { it.name })
        assertEquals(listOf(url), subscriptions.allUrls())
    }
}
