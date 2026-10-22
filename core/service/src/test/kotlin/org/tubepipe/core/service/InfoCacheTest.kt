package org.tubepipe.core.service

import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoCacheTest {
    private var nowMillis = 0L
    private val fetchedKeys = mutableListOf<String>()

    private fun cache(
        timeToLiveMillis: Long = 60_000,
        maxEntries: Int = 4,
        fetch: suspend (String) -> String = { key -> "info:$key" },
    ) = InfoCache<String, String>(
        fetch = { key ->
            fetchedKeys += key
            fetch(key)
        },
        clock = { nowMillis },
        timeToLiveMillis = timeToLiveMillis,
        maxEntries = maxEntries,
    )

    // A prefetched video that is then played must not be fetched a second time.
    @Test
    fun secondGetWithinTimeToLiveReusesTheValue() = runTest(timeout = 10.seconds) {
        val cache = cache()

        assertEquals("info:a", cache.get("a"))
        nowMillis = 59_000
        assertEquals("info:a", cache.get("a"))

        assertEquals(listOf("a"), fetchedKeys)
    }

    @Test
    fun valueOlderThanTimeToLiveIsFetchedAgain() = runTest(timeout = 10.seconds) {
        val cache = cache()

        cache.get("a")
        nowMillis = 60_001
        cache.get("a")

        assertEquals(listOf("a", "a"), fetchedKeys)
    }

    // Centre pressed while the prefetch of the same video is still running joins it instead of sending a second request.
    @Test
    fun getDuringAnInFlightFetchJoinsIt() = runTest(timeout = 10.seconds) {
        val response = CompletableDeferred<String>()
        val cache = cache(fetch = { response.await() })

        val prefetch = async { cache.get("a") }
        runCurrent()
        assertTrue(cache.contains("a"))
        val play = async { cache.get("a") }
        runCurrent()
        response.complete("info")

        assertEquals("info", prefetch.await())
        assertEquals("info", play.await())
        assertEquals(listOf("a"), fetchedKeys)
    }

    @Test
    fun failedFetchIsNotCached() = runTest(timeout = 10.seconds) {
        var shouldFail = true
        val cache = cache(fetch = { key ->
            if (shouldFail) throw IOException("offline")
            "info:$key"
        })

        val failure = runCatching { cache.get("a") }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertFalse(cache.contains("a"))

        shouldFail = false
        assertEquals("info:a", cache.get("a"))
        assertEquals(listOf("a", "a"), fetchedKeys)
    }

    // A cached stream that failed to play is dropped, so the retry asks YouTube again.
    @Test
    fun invalidatedValueIsFetchedAgain() = runTest(timeout = 10.seconds) {
        val cache = cache()

        cache.get("a")
        cache.invalidate("a")

        assertFalse(cache.contains("a"))
        cache.get("a")
        assertEquals(listOf("a", "a"), fetchedKeys)
    }

    @Test
    fun containsIsFalseForExpiredValues() = runTest(timeout = 10.seconds) {
        val cache = cache()

        cache.get("a")
        nowMillis = 60_001

        assertFalse(cache.contains("a"))
    }

    @Test
    fun oldestEntryIsDroppedPastMaxEntries() = runTest(timeout = 10.seconds) {
        val cache = cache(maxEntries = 2)

        cache.get("a")
        nowMillis = 1
        cache.get("b")
        nowMillis = 2
        cache.get("c")

        assertFalse(cache.contains("a"))
        assertTrue(cache.contains("b"))
        assertTrue(cache.contains("c"))
    }
}
