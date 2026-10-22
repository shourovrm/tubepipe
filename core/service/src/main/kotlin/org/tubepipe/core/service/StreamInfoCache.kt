package org.tubepipe.core.service

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * The process-wide cache of YouTube stream info. The video page (details) and each player client's stream list are
 * separate requests, so a video has one key per client. Prefetching fills it; playing reads through it.
 */
object StreamInfoCache {
    data class Key(val videoUrl: String, val playerClientName: String?)

    // The player client is a process-wide extractor setting, so two fetches must never interleave
    // "set the client" and "get the info".
    private val fetchMutex = Mutex()

    private val cache = InfoCache<Key, StreamInfo>(fetch = ::fetchStreamInfo, maxEntries = MAX_ENTRIES)

    /**
     * The info of [videoUrl] fetched with the player client named [playerClientName]; null leaves the client as it is
     * (the video page request).
     */
    suspend fun get(videoUrl: String, playerClientName: String?): StreamInfo {
        try {
            return cache.get(Key(videoUrl, playerClientName))
        } catch (failure: ExtractionException) {
            // Callers outside this module cannot see ExtractionException (see RepositoryErrorTest).
            throw IOException("YouTube stream info failed for $videoUrl: ${failure.message}", failure)
        }
    }

    /** Fills the cache for [videoUrl] without handing the result out, for callers that cannot see [StreamInfo]. */
    suspend fun prefetch(videoUrl: String, playerClientName: String?) {
        get(videoUrl, playerClientName)
    }

    fun contains(videoUrl: String, playerClientName: String?): Boolean = cache.contains(Key(videoUrl, playerClientName))

    fun invalidate(videoUrl: String, playerClientName: String?) {
        cache.invalidate(Key(videoUrl, playerClientName))
    }

    /** Drops every cached request of [videoUrl], whichever client made it, so the next play asks YouTube again. */
    fun invalidateVideo(videoUrl: String) {
        cache.invalidateWhere { key -> key.videoUrl == videoUrl }
    }

    private suspend fun fetchStreamInfo(key: Key): StreamInfo = fetchMutex.withLock {
        withContext(Dispatchers.IO) {
            if (key.playerClientName != null) {
                NewPipe.setYoutubePlayerClient(key.playerClientName)
            }
            StreamInfo.getInfo(ServiceList.YouTube, key.videoUrl)
        }
    }

    // Two keys per video (details and the preferred client), plus the fallback client and the next video.
    private const val MAX_ENTRIES = 16
}
