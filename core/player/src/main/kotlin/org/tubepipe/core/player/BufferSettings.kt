package org.tubepipe.core.player

/**
 * Load control durations in milliseconds. Media3's own defaults start playback after 1 s of buffer and
 * ExoPlayer 2 waited for 2.5 s; 1.5 s starts a second sooner on the box and the 25 s target buffer catches up.
 */
object BufferSettings {
    const val MIN_BUFFER_MS = 25_000
    const val MAX_BUFFER_MS = 50_000
    const val BUFFER_FOR_PLAYBACK_MS = 1_500
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000

    /** Keeps 60 s behind the playhead so a 30 s seek back is served from memory, not the network. */
    const val BACK_BUFFER_MS = 60_000
}
