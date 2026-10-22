package org.tubepipe.tv.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** True in the last 30 s of a video; false for a live stream or a video still loading, which have no duration. */
fun isNextVideoPrefetchDue(positionMillis: Long, durationMillis: Long): Boolean {
    if (durationMillis <= 0) {
        return false
    }
    return durationMillis - positionMillis <= NEXT_VIDEO_PREFETCH_WINDOW_MILLIS
}

private const val NEXT_VIDEO_PREFETCH_WINDOW_MILLIS = 30_000L
private const val RATE_WINDOW_MILLIS = 60_000L

/**
 * Decides when to fetch a video ahead of the viewer pressing Centre. The rules keep the request count low, because
 * YouTube may flag a client that sends many requests: a card must keep focus for [DWELL_MILLIS], at most
 * [MAX_PREFETCHES_PER_MINUTE] prefetches start in any 60 s, only one runs at a time, and a video that is
 * already cached is not fetched again. A prefetch skipped by the cap is dropped, not queued.
 *
 * [prefetch] does the fetching and [isCached] tells whether it is still needed. [clock] is in milliseconds.
 */
class PrefetchScheduler(
    private val scope: CoroutineScope,
    private val prefetch: suspend (videoUrl: String) -> Unit,
    private val isCached: (videoUrl: String) -> Boolean,
    private val clock: () -> Long,
) {
    private val prefetchMutex = Mutex()

    // Only touched while holding prefetchMutex.
    private val startTimesMillis = ArrayDeque<Long>()

    private var pendingDwell: Job? = null

    /** Prefetches [videoUrl] once it has kept focus for [DWELL_MILLIS]; focusing another card restarts the wait. */
    fun onCardFocused(videoUrl: String) {
        pendingDwell?.cancel()
        pendingDwell = scope.launch {
            delay(DWELL_MILLIS)
            // A separate job: cancelling the dwell for the next card must not cancel a request already sent.
            prefetchIfAllowed(videoUrl)
        }
    }

    /** Cancels the dwell wait only; a prefetch that already started runs to completion. */
    fun cancelPending() {
        pendingDwell?.cancel()
        pendingDwell = null
    }

    /** Prefetches without a dwell, for a video that is certain to be played next. */
    fun prefetchNow(videoUrl: String) {
        scope.launch { prefetchIfAllowed(videoUrl) }
    }

    private suspend fun prefetchIfAllowed(videoUrl: String) {
        prefetchMutex.withLock {
            // Checked inside the lock: an earlier prefetch of the same video may have just filled the cache.
            if (isCached(videoUrl) || !isWithinRateCap()) {
                return
            }
            startTimesMillis.addLast(clock())
            prefetch(videoUrl)
        }
    }

    private fun isWithinRateCap(): Boolean {
        val now = clock()
        while (startTimesMillis.isNotEmpty() && now - startTimesMillis.first() >= RATE_WINDOW_MILLIS) {
            startTimesMillis.removeFirst()
        }
        return startTimesMillis.size < MAX_PREFETCHES_PER_MINUTE
    }

    companion object {
        const val DWELL_MILLIS = 1_150L
        const val MAX_PREFETCHES_PER_MINUTE = 6
    }
}
