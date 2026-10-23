package org.tubepipe.tv.player

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import java.io.IOException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.tubepipe.core.player.CaptionOption
import org.tubepipe.core.player.PlaybackPreferences
import org.tubepipe.core.player.PlayerClient
import org.tubepipe.core.player.TubepipePlayer

private const val TAG = "TubepipePlayerEngine"

enum class LoadState { Idle, Loading, Loaded, Failed }

/** One audio track the viewer can switch to, for videos that carry several languages. */
class AudioOption(val label: String, internal val trackGroup: TrackGroup)

/**
 * What the screens draw from a running player. The playback session writes it (listener and position poll);
 * the full-screen controls and the corner window read it.
 */
@Stable
class PlaybackSnapshot {
    var isPlaying by mutableStateOf(false)
    var playWhenReady by mutableStateOf(false)
    var positionMillis by mutableLongStateOf(0L)
    var durationMillis by mutableLongStateOf(0L)
    var captionText by mutableStateOf("")
    var skippedCategory by mutableStateOf<String?>(null)

    /** True once the player was ready at least once; only then is its position worth saving. */
    var hasStarted = false
}

/**
 * Adapter that gives [PlaybackController] a [TubepipePlayer]. The player is created in [load], not in the
 * constructor, because its quality, codec and caption defaults come from the video's view model.
 */
@Stable
class TubepipePlayerEngine(private val context: Context) : PlayerEngine {
    val snapshot = PlaybackSnapshot()

    var player by mutableStateOf<TubepipePlayer?>(null)
        private set
    var loadState by mutableStateOf(LoadState.Idle)
        private set

    /** What the controls show as current; null quality and captions mean Auto and Off. */
    var currentSpeed by mutableFloatStateOf(1f)
        private set
    var currentQuality by mutableStateOf<Int?>(null)
        private set
    var captionLanguage by mutableStateOf<String?>(null)
        private set

    private var isReleased = false

    // Quality switches outlive the menu that started them, but not the engine.
    private val qualityScope = MainScope()

    // Kept here because the loop choice can arrive before load() has built the player.
    private var isLooping = false

    // Controls act on playWhenReady: while the player buffers, isPlaying is false but the viewer pressed play.
    override val isPlaying: Boolean get() = player?.exoPlayer?.playWhenReady ?: false

    override fun pause() {
        player?.exoPlayer?.pause()
    }

    override fun resume() {
        player?.exoPlayer?.play()
    }

    override fun setLooping(isLooping: Boolean) {
        this.isLooping = isLooping
        player?.exoPlayer?.repeatMode = repeatModeFor(isLooping)
    }

    override fun release() {
        isReleased = true
        qualityScope.cancel()
        player?.release()
    }

    /** Extracts and starts [videoUrl]; sets [loadState] to Failed instead of throwing when no client works. */
    suspend fun load(
        client: PlayerClient,
        preferences: PlaybackPreferences,
        videoUrl: String,
        startPositionMillis: Long,
    ) {
        if (isReleased) {
            return
        }
        loadState = LoadState.Loading
        currentSpeed = preferences.speed
        currentQuality = preferences.maxVideoHeight
        captionLanguage = preferences.captionLanguage
        val newPlayer = TubepipePlayer(context, client, preferences)
        newPlayer.exoPlayer.repeatMode = repeatModeFor(isLooping)
        player = newPlayer
        try {
            newPlayer.load(videoUrl, startPositionMillis)
            loadState = LoadState.Loaded
        } catch (failure: IOException) {
            Log.e(TAG, "Playing $videoUrl failed", failure)
            loadState = LoadState.Failed
        }
    }

    fun qualityOptions(): List<Int> = player?.qualityOptions().orEmpty()

    fun captionOptions(): List<CaptionOption> = player?.captionOptions().orEmpty()

    fun setSpeed(newSpeed: Float) {
        player?.setSpeed(newSpeed)
        currentSpeed = newSpeed
    }

    fun setQuality(height: Int?) {
        currentQuality = height
        val currentPlayer = player ?: return
        // Rebuilding a SABR stream does network I/O, so it runs off the menu click.
        qualityScope.launch {
            try {
                currentPlayer.setMaxQuality(height)
            } catch (failure: IOException) {
                Log.e(TAG, "Switching to quality $height failed; the current stream keeps playing", failure)
            }
        }
    }

    fun setCaptions(language: String?) {
        player?.setCaptions(language)
        captionLanguage = language
    }

    /** Empty unless the video has at least two audio tracks. */
    fun audioOptions(): List<AudioOption> {
        val exoPlayer = player?.exoPlayer ?: return emptyList()
        val options = exoPlayer.currentTracks.groups
            .filter { group -> group.type == C.TRACK_TYPE_AUDIO }
            .map { group ->
                val format = group.getTrackFormat(0)
                AudioOption(format.label ?: format.language ?: "?", group.mediaTrackGroup)
            }
        return if (options.size > 1) options else emptyList()
    }

    fun selectAudio(option: AudioOption) {
        val exoPlayer = player?.exoPlayer ?: return
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(option.trackGroup, 0))
            .build()
    }
}

private fun repeatModeFor(isLooping: Boolean): Int = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
