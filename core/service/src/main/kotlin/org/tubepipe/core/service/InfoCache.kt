package org.tubepipe.core.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Keeps the result of an expensive fetch for [timeToLiveMillis] and lets concurrent callers share one fetch.
 * Safe to call from any thread.
 *
 * Every fetch runs in a scope the cache owns, so a caller that is cancelled (the user pressed Back) does not
 * cancel a fetch that another caller waits on, and a started fetch always ends up in the cache.
 * A failed fetch is not cached; every caller waiting on it receives the exception.
 */
class InfoCache<K : Any, V>(
    private val fetch: suspend (K) -> V,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeToLiveMillis: Long = DEFAULT_TIME_TO_LIVE_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private class Entry<V>(val result: Deferred<V>) {
        /** Null while the fetch is in flight. */
        var storedAtMillis: Long? = null
    }

    private val lock = Any()
    private val entries = HashMap<K, Entry<V>>()
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The fresh cached value, else the result of the fetch already in flight for [key], else a new fetch. */
    suspend fun get(key: K): V {
        val entry = synchronized(lock) { usableEntry(key) ?: startFetch(key) }
        return entry.result.await()
    }

    /** True when [key] has a fresh value or a fetch in flight, so [get] would send no new request. */
    fun contains(key: K): Boolean = synchronized(lock) { usableEntry(key) != null }

    fun invalidate(key: K) {
        synchronized(lock) { entries.remove(key) }
    }

    /** Drops every key for which [shouldDrop] is true; a fetch in flight still finishes but is not stored. */
    fun invalidateWhere(shouldDrop: (K) -> Boolean) {
        synchronized(lock) { entries.keys.removeAll(shouldDrop) }
    }

    // Callers hold the lock.
    private fun usableEntry(key: K): Entry<V>? {
        val entry = entries[key] ?: return null
        val storedAtMillis = entry.storedAtMillis ?: return entry
        if (clock() - storedAtMillis <= timeToLiveMillis) {
            return entry
        }
        entries.remove(key)
        return null
    }

    // Callers hold the lock.
    private fun startFetch(key: K): Entry<V> {
        // The entry needs the deferred and the deferred's body needs the entry, so the body reads it late.
        lateinit var entry: Entry<V>
        val result = fetchScope.async(start = CoroutineStart.LAZY) {
            try {
                fetch(key).also { value -> store(key, entry) }
            } catch (failure: Throwable) {
                forget(key, entry)
                throw failure
            }
        }
        entry = Entry(result)
        entries[key] = entry
        result.start()
        return entry
    }

    private fun store(key: K, entry: Entry<V>) {
        synchronized(lock) {
            // Invalidated while in flight: the value is returned to the callers waiting but not kept.
            if (entries[key] !== entry) {
                return
            }
            entry.storedAtMillis = clock()
            dropOldestPastLimit()
        }
    }

    private fun forget(key: K, entry: Entry<V>) {
        synchronized(lock) {
            if (entries[key] === entry) {
                entries.remove(key)
            }
        }
    }

    // Callers hold the lock. A fetch in flight is never dropped: someone is waiting on it.
    private fun dropOldestPastLimit() {
        while (entries.size > maxEntries) {
            val oldest = entries.entries
                .filter { (_, entry) -> entry.storedAtMillis != null }
                .minByOrNull { (_, entry) -> entry.storedAtMillis!! }
                ?: return
            entries.remove(oldest.key)
        }
    }

    private companion object {
        const val DEFAULT_TIME_TO_LIVE_MILLIS = 5 * 60 * 1_000L
        const val DEFAULT_MAX_ENTRIES = 8
    }
}
