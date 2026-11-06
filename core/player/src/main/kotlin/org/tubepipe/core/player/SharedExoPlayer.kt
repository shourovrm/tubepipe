package org.tubepipe.core.player

import android.content.Context
import androidx.annotation.MainThread
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * The one ExoPlayer of the process. Building a player enumerates the box's codecs and allocates renderers,
 * which cost the better part of a second on every video when each [TubepipePlayer] made its own; now a video
 * only sets its media source and track parameters on the existing player.
 */
object SharedExoPlayer {
    private var instance: ExoPlayer? = null

    @MainThread
    fun get(context: Context): ExoPlayer {
        instance?.let { return it }
        val applicationContext = context.applicationContext
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                BufferSettings.MIN_BUFFER_MS,
                BufferSettings.MAX_BUFFER_MS,
                BufferSettings.BUFFER_FOR_PLAYBACK_MS,
                BufferSettings.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setBackBuffer(BufferSettings.BACK_BUFFER_MS, true)
            .build()
        val created = ExoPlayer.Builder(applicationContext)
            .setLoadControl(loadControl)
            .setTrackSelector(DefaultTrackSelector(applicationContext))
            .build()
        instance = created
        return created
    }
}
