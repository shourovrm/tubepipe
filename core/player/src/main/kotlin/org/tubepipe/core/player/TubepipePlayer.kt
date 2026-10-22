package org.tubepipe.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Loads a YouTube URL into an [ExoPlayer] and starts playback. Create it and call [load] on the main thread;
 * [PlaybackRuntime.initialize] and the extractor start must have run first.
 */
@OptIn(UnstableApi::class)
class TubepipePlayer(context: Context, private val preferredClient: PlayerClient = PlayerClient.DEFAULT) {
    private val sourceFactory = StreamMediaSourceFactory(context.applicationContext)

    val exoPlayer: ExoPlayer = createExoPlayer(context.applicationContext)

    private val originalAudioTrackSelector = OriginalAudioTrackSelector(exoPlayer)

    /**
     * Extracts [videoUrl] with the preferred client, then with the other client if that fails, and plays the first
     * stream that builds. Extraction and source building run on [Dispatchers.IO]; the player is prepared on the
     * calling (main) thread.
     *
     * @throws IOException when every client failed; the failure of each client is attached as a suppressed exception
     */
    suspend fun load(videoUrl: String, startPositionMs: Long = 0L): LoadedStream {
        val failures = mutableListOf<Pair<PlayerClient, Exception>>()
        for (client in PlayerClient.fallbackOrder(preferredClient)) {
            try {
                val loaded = withContext(Dispatchers.IO) { extractAndBuild(client, videoUrl, startPositionMs) }
                chooseAudioTrackAfterPrepare(loaded.builtSource.delivery)
                exoPlayer.setMediaSource(loaded.builtSource.mediaSource, startPositionMs)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                return LoadedStream(client, loaded.builtSource.delivery, loaded.streamInfo.name)
            } catch (error: IOException) {
                failures.add(client to error)
            } catch (error: ExtractionException) {
                failures.add(client to error)
            }
        }
        throw IOException(failureMessage(videoUrl, failures)).also { combined ->
            failures.forEach { (_, error) -> combined.addSuppressed(error) }
        }
    }

    /** Only HLS lists audio languages as separate tracks; SABR picks its audio track while building the source. */
    private fun chooseAudioTrackAfterPrepare(delivery: Delivery) {
        exoPlayer.removeListener(originalAudioTrackSelector)
        if (delivery == Delivery.Hls) {
            originalAudioTrackSelector.reset()
            exoPlayer.addListener(originalAudioTrackSelector)
        }
    }

    fun release() {
        exoPlayer.release()
    }

    private class ExtractedSource(val streamInfo: StreamInfo, val builtSource: BuiltMediaSource)

    private fun extractAndBuild(client: PlayerClient, videoUrl: String, startPositionMs: Long): ExtractedSource {
        // The extractor reads the client from a process-wide setting, so it is set right before each attempt.
        NewPipe.setYoutubePlayerClient(client.extractorName)
        val streamInfo = StreamInfo.getInfo(videoUrl)
        return ExtractedSource(streamInfo, sourceFactory.build(streamInfo, startPositionMs))
    }

    private fun failureMessage(videoUrl: String, failures: List<Pair<PlayerClient, Exception>>): String {
        val perClient = failures.joinToString(separator = "; ") { (client, error) ->
            "${client.extractorName}: ${error.message}"
        }
        return "Could not load $videoUrl with any player client ($perClient)"
    }

    private fun createExoPlayer(applicationContext: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                BufferSettings.MIN_BUFFER_MS,
                BufferSettings.MAX_BUFFER_MS,
                BufferSettings.BUFFER_FOR_PLAYBACK_MS,
                BufferSettings.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setBackBuffer(BufferSettings.BACK_BUFFER_MS, true)
            .build()
        val trackSelector = DefaultTrackSelector(applicationContext).apply {
            // The Mi Box S outputs 1080p; HLS master playlists also offer taller variants.
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(
                        StreamMediaSourceFactory.MAX_VIDEO_WIDTH,
                        StreamMediaSourceFactory.MAX_VIDEO_HEIGHT,
                    )
                    // Without an explicit viewport Media3 sized it from the context's display and kept only variants
                    // up to 480p on the box, although the panel is 1080p.
                    .setViewportSize(
                        StreamMediaSourceFactory.MAX_VIDEO_WIDTH,
                        StreamMediaSourceFactory.MAX_VIDEO_HEIGHT,
                        true,
                    )
                    // avc1 has hardware decoding on the box; this keeps adaptive HLS off the VP9 variants.
                    .setPreferredVideoMimeType(MimeTypes.VIDEO_H264),
            )
        }
        return ExoPlayer.Builder(applicationContext)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
    }
}
