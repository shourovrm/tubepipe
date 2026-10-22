package org.tubepipe.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
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
 * [preferences] are the user's defaults; the quality, caption and speed setters change them for this player.
 */
@OptIn(UnstableApi::class)
class TubepipePlayer(
    context: Context,
    private val preferredClient: PlayerClient = PlayerClient.DEFAULT,
    private val preferences: PlaybackPreferences = PlaybackPreferences(),
) {
    private val sourceFactory = StreamMediaSourceFactory(
        context.applicationContext,
        maxVideoHeight = preferences.maxVideoHeight ?: AUTO_MAX_VIDEO_HEIGHT,
        preferredCodecPrefix = preferences.preferredCodec.codecPrefix,
        preferredAudioLanguage = preferences.preferredAudioLanguage,
    )

    val exoPlayer: ExoPlayer = createExoPlayer(context.applicationContext)

    private val originalAudioTrackSelector =
        OriginalAudioTrackSelector(exoPlayer, preferences.preferredAudioLanguage)

    init {
        exoPlayer.setPlaybackSpeed(preferences.speed)
    }

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

    /** Heights the loaded video offers on this device, tallest first; empty until the tracks are known. */
    fun qualityOptions(): List<Int> = TrackMenus.qualities(exoPlayer.currentTracks)

    /**
     * Caps the picture height of adaptive streams (HLS, DASH); null is automatic, up to 1080. A SABR or single
     * progressive stream is fixed when [load] builds it, so this does not change it until the next [load].
     */
    fun setMaxQuality(height: Int?) {
        exoPlayer.trackSelectionParameters = withVideoLimits(
            exoPlayer.trackSelectionParameters.buildUpon(),
            height ?: AUTO_MAX_VIDEO_HEIGHT,
        ).build()
    }

    /** Caption languages of the loaded video; empty until the tracks are known. */
    fun captionOptions(): List<CaptionOption> = TrackMenus.captions(exoPlayer.currentTracks)

    /** Shows the captions in [language] (a [CaptionOption.language]); null turns captions off. */
    fun setCaptions(language: String?) {
        val parameters = exoPlayer.trackSelectionParameters.buildUpon()
        if (language == null) {
            exoPlayer.trackSelectionParameters = parameters
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }
        parameters.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        parameters.setPreferredTextLanguage(language)
        val trackGroup = textTrackGroupOf(language)
        if (trackGroup != null) {
            // An override picks exactly this track; the preferred language alone could match a sibling such as
            // an auto-generated track of the same language.
            parameters.setOverrideForType(TrackSelectionOverride(trackGroup, 0))
        }
        exoPlayer.trackSelectionParameters = parameters.build()
    }

    fun setSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    private fun textTrackGroupOf(language: String): TrackGroup? {
        val textGroups = exoPlayer.currentTracks.groups.filter { group -> group.type == C.TRACK_TYPE_TEXT }
        val matching = textGroups.firstOrNull { group -> group.getTrackFormat(0).language == language }
        return matching?.mediaTrackGroup
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

    private fun withVideoLimits(
        builder: TrackSelectionParameters.Builder,
        maxHeight: Int,
    ): TrackSelectionParameters.Builder = builder
        // The Mi Box S outputs 1080p; HLS master playlists also offer taller variants.
        .setMaxVideoSize(Int.MAX_VALUE, maxHeight)
        // Without an explicit viewport Media3 sized it from the context's display and kept only variants
        // up to 480p on the box, although the panel is 1080p.
        .setViewportSize(StreamMediaSourceFactory.MAX_VIDEO_WIDTH, maxHeight, true)

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
        val trackSelector = DefaultTrackSelector(applicationContext)
        val parameters = trackSelector.buildUponParameters()
        withVideoLimits(parameters, preferences.maxVideoHeight ?: AUTO_MAX_VIDEO_HEIGHT)
            // avc1 has hardware decoding on the box; the default keeps adaptive HLS off the VP9 variants.
            .setPreferredVideoMimeType(preferences.preferredCodec.mimeType)
        applyStartingCaptions(parameters)
        trackSelector.setParameters(parameters)
        return ExoPlayer.Builder(applicationContext)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
    }

    private fun applyStartingCaptions(parameters: TrackSelectionParameters.Builder) {
        val captionLanguage = preferences.captionLanguage
        if (captionLanguage == null) {
            // Media3 turns on a text track that is flagged default; off must mean off.
            parameters.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            parameters.setPreferredTextLanguage(captionLanguage)
        }
    }

    private companion object {
        const val AUTO_MAX_VIDEO_HEIGHT = StreamMediaSourceFactory.MAX_VIDEO_HEIGHT
    }
}
