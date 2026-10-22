package org.tubepipe.tv.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PlayerMode { Fullscreen, Mini }

/** The video that is loaded in the player, and whether it fills the screen or sits in the corner. */
data class NowPlaying(val videoUrl: String, val mode: PlayerMode)

/** What the controller needs from a player; the app wraps TubepipePlayer in [TubepipePlayerEngine]. */
interface PlayerEngine {
    val isPlaying: Boolean

    fun pause()

    fun resume()

    fun release()

    /** Repeats the current video forever when true. */
    fun setLooping(isLooping: Boolean)
}

class EngineLease(val engine: PlayerEngine, val isNew: Boolean)

/**
 * Owns the one running player for the whole app, so it survives screen changes: leaving the player screen
 * minimises it into the corner window instead of stopping it. Call every method on the main thread.
 */
class PlaybackController(private val createEngine: () -> PlayerEngine) {
    private val mutableState = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = mutableState

    private val mutableEngine = MutableStateFlow<PlayerEngine?>(null)

    /**
     * The engine of [state], or null when nothing is playing. Separate from [state] because a retry replaces
     * the engine while the state stays equal, and collectors of [state] would not notice.
     */
    val engine: StateFlow<PlayerEngine?> = mutableEngine

    val currentEngine: PlayerEngine? get() = mutableEngine.value

    private val mutableIsLooping = MutableStateFlow(false)

    /** Whether the playing video repeats; the choice carries over to the next video until [stop]. */
    val isLooping: StateFlow<Boolean> = mutableIsLooping

    // Videos played before the current one, oldest first; the last entry is what "Previous" opens.
    private val previousVideos = ArrayDeque<String>()

    /**
     * Shows [videoUrl] full screen. The engine already running that video is reused; for any other video the
     * running engine is released and a new one is created, which the caller must load.
     */
    fun engineFor(videoUrl: String): EngineLease {
        val running = currentEngine
        val current = mutableState.value
        if (running != null && current != null && current.videoUrl == videoUrl) {
            mutableState.value = NowPlaying(videoUrl, PlayerMode.Fullscreen)
            return EngineLease(running, isNew = false)
        }
        if (current != null) {
            rememberPrevious(leaving = current.videoUrl, opening = videoUrl)
        }
        running?.release()
        return startEngine(videoUrl)
    }

    /** Replaces the engine of the current video with a fresh one, for a retry; the history is kept. */
    fun restart(videoUrl: String): EngineLease {
        currentEngine?.release()
        return startEngine(videoUrl)
    }

    fun minimize() {
        val current = mutableState.value ?: return
        mutableState.value = current.copy(mode = PlayerMode.Mini)
    }

    fun expand() {
        val current = mutableState.value ?: return
        mutableState.value = current.copy(mode = PlayerMode.Fullscreen)
    }

    fun stop() {
        currentEngine?.release()
        mutableEngine.value = null
        previousVideos.clear()
        mutableIsLooping.value = false
        mutableState.value = null
    }

    fun toggleLoop() {
        val looping = !mutableIsLooping.value
        mutableIsLooping.value = looping
        currentEngine?.setLooping(looping)
    }

    fun previousVideoUrl(): String? = previousVideos.lastOrNull()

    fun togglePause() {
        val engine = currentEngine ?: return
        if (engine.isPlaying) engine.pause() else engine.resume()
    }

    private fun startEngine(videoUrl: String): EngineLease {
        val engine = createEngine()
        engine.setLooping(mutableIsLooping.value)
        mutableEngine.value = engine
        mutableState.value = NowPlaying(videoUrl, PlayerMode.Fullscreen)
        return EngineLease(engine, isNew = true)
    }

    private fun rememberPrevious(leaving: String, opening: String) {
        // Going back to the previous video drops it from the list instead of stacking the same one twice.
        if (previousVideos.lastOrNull() == opening) {
            previousVideos.removeLast()
        } else {
            previousVideos.addLast(leaving)
        }
    }
}
