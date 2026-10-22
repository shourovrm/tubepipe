package org.tubepipe.core.player

/** Load control durations in milliseconds; Media3's own defaults start playback after 1 s of buffer. */
object BufferSettings {
    const val MIN_BUFFER_MS = 25_000
    const val MAX_BUFFER_MS = 50_000
    const val BUFFER_FOR_PLAYBACK_MS = 2_500
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000

    /** Keeps 60 s behind the playhead so a 30 s seek back is served from memory, not the network. */
    const val BACK_BUFFER_MS = 60_000
}
