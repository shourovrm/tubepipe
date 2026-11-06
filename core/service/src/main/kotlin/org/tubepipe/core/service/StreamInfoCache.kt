package org.tubepipe.core.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * The last few extractions, shared by the detail page, the Home rows and the player, so one video is
 * extracted once instead of by every caller. An entry is only served to the player client it was
 * extracted with, and is dropped after [MAX_AGE_MS] because the stream URLs inside expire.
 *
 * A fetch runs on its own scope: a caller that is cancelled (Home reloads on every saved playback
 * position) does not throw away an extraction that is seconds from done, and concurrent callers
 * for the same video wait for the one in flight.
 */
object StreamInfoCache {
    private class Entry(val client: String, val streamInfo: StreamInfo, val storedAtMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > MAX_ENTRIES
    }
    private val inFlight = mutableMapOf<String, Deferred<StreamInfo>>()
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getOrFetch(url: String, client: String, fetch: () -> StreamInfo): StreamInfo {
        get(url, client)?.let { return it }
        val key = "$client $url"
        val deferred = synchronized(this) {
            inFlight.getOrPut(key) {
                fetchScope.async {
                    try {
                        fetch().also { fresh -> put(url, client, fresh) }
                    } finally {
                        synchronized(this@StreamInfoCache) { inFlight.remove(key) }
                    }
                }
            }
        }
        return deferred.await()
    }

    @Synchronized
    fun remove(url: String) {
        entries.remove(url)
    }

    @Synchronized
    private fun put(url: String, client: String, streamInfo: StreamInfo) {
        entries[url] = Entry(client, streamInfo, System.currentTimeMillis())
    }

    @Synchronized
    private fun get(url: String, client: String): StreamInfo? {
        val entry = entries[url] ?: return null
        if (entry.client != client || System.currentTimeMillis() - entry.storedAtMs > MAX_AGE_MS) {
            entries.remove(url)
            return null
        }
        return entry.streamInfo
    }

    private const val MAX_ENTRIES = 8
    private const val LOAD_FACTOR = 0.75f
    private const val MAX_AGE_MS = 30L * 60L * 1000L
}
