package org.tubepipe.tv.player

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchSchedulerTest {
    private val prefetchedUrls = mutableListOf<String>()
    private val cachedUrls = mutableSetOf<String>()

    private fun TestScope.scheduler() = PrefetchScheduler(
        scope = backgroundScope,
        prefetch = { videoUrl ->
            prefetchedUrls += videoUrl
            cachedUrls += videoUrl
        },
        isCached = { videoUrl -> videoUrl in cachedUrls },
        clock = { testScheduler.currentTime },
    )

    private fun TestScope.waitMillis(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun cardFocusedForTheDwellTimeIsPrefetched() = runTest(timeout = 10.seconds) {
        val scheduler = scheduler()

        scheduler.onCardFocused("a")
        waitMillis(PrefetchScheduler.DWELL_MILLIS - 1)
        assertEquals(emptyList<String>(), prefetchedUrls)

        waitMillis(1)
        assertEquals(listOf("a"), prefetchedUrls)
    }

    // Holding Down across a row must send nothing for the cards passed on the way.
    @Test
    fun cardsPassedBeforeTheDwellTimeAreNotPrefetched() = runTest(timeout = 10.seconds) {
        val scheduler = scheduler()

        scheduler.onCardFocused("a")
        waitMillis(300)
        scheduler.onCardFocused("b")
        waitMillis(300)
        scheduler.onCardFocused("c")
        waitMillis(PrefetchScheduler.DWELL_MILLIS)

        assertEquals(listOf("c"), prefetchedUrls)
    }

    @Test
    fun focusLeavingTheCardsCancelsThePendingPrefetch() = runTest(timeout = 10.seconds) {
        val scheduler = scheduler()

        scheduler.onCardFocused("a")
        waitMillis(300)
        scheduler.cancelPending()
        waitMillis(PrefetchScheduler.DWELL_MILLIS)

        assertEquals(emptyList<String>(), prefetchedUrls)
    }

    @Test
    fun cachedVideoIsNotPrefetchedAgain() = runTest(timeout = 10.seconds) {
        val scheduler = scheduler()
        cachedUrls += "a"

        scheduler.onCardFocused("a")
        waitMillis(PrefetchScheduler.DWELL_MILLIS)

        assertEquals(emptyList<String>(), prefetchedUrls)
    }

    // The cap keeps a long browse close to what YouTube's own hover previews send.
    @Test
    fun prefetchesPastTheCapPerMinuteAreSkippedUntilTheMinutePasses() = runTest(timeout = 10.seconds) {
        val scheduler = scheduler()
        val cap = PrefetchScheduler.MAX_PREFETCHES_PER_MINUTE

        for (index in 1..cap + 1) {
            scheduler.onCardFocused("video$index")
            waitMillis(PrefetchScheduler.DWELL_MILLIS)
        }
        assertEquals(cap, prefetchedUrls.size)
        assertFalse("video${cap + 1}" in prefetchedUrls)

        waitMillis(60_000)
        scheduler.onCardFocused("late")
        waitMillis(PrefetchScheduler.DWELL_MILLIS)
        assertTrue("late" in prefetchedUrls)
    }

    @Test
    fun prefetchNowSkipsTheDwellButKeepsTheCapAndCacheRules() = runTest(timeout = 10.seconds) {
        val scheduler = scheduler()

        scheduler.prefetchNow("next")
        runCurrent()
        assertEquals(listOf("next"), prefetchedUrls)

        scheduler.prefetchNow("next")
        runCurrent()
        assertEquals(listOf("next"), prefetchedUrls)
    }

    @Test
    fun nextVideoIsDueInTheLastThirtySeconds() {
        assertFalse(isNextVideoPrefetchDue(positionMillis = 0, durationMillis = 300_000))
        assertFalse(isNextVideoPrefetchDue(positionMillis = 269_999, durationMillis = 300_000))
        assertTrue(isNextVideoPrefetchDue(positionMillis = 270_000, durationMillis = 300_000))
    }

    @Test
    fun shortVideoIsDueAtOnce() {
        assertTrue(isNextVideoPrefetchDue(positionMillis = 0, durationMillis = 20_000))
    }

    // A live stream or a video still loading reports no duration; there is no end to prefetch for.
    @Test
    fun unknownDurationIsNeverDue() {
        assertFalse(isNextVideoPrefetchDue(positionMillis = 5_000, durationMillis = 0))
        assertFalse(isNextVideoPrefetchDue(positionMillis = 5_000, durationMillis = -1))
    }
}
