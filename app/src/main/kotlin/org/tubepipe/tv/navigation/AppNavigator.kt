package org.tubepipe.tv.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.tubepipe.tv.Destination

sealed interface Screen {
    data class Top(val destination: Destination) : Screen

    data class Channel(val channelUrl: String) : Screen

    /** A video; [queue] is set while it plays as part of a playlist. */
    data class Player(val videoUrl: String, val queue: PlaybackQueue? = null) : Screen

    data class Playlist(val source: PlaylistSource) : Screen
}

/** Where a playlist comes from: a YouTube playlist page, or one the user made in the Library. */
sealed interface PlaylistSource {
    data class Remote(val playlistUrl: String) : PlaylistSource

    data class Local(val playlistId: Long) : PlaylistSource
}

/** The videos of a playlist in play order; [index] is the one playing. */
data class PlaybackQueue(val source: PlaylistSource, val title: String, val videoUrls: List<String>, val index: Int) {
    init {
        require(index in videoUrls.indices) { "index $index is outside a queue of ${videoUrls.size}" }
    }

    val currentUrl: String get() = videoUrls[index]

    fun next(): PlaybackQueue? = if (index + 1 < videoUrls.size) copy(index = index + 1) else null

    fun previous(): PlaybackQueue? = if (index > 0) copy(index = index - 1) else null
}

/**
 * The screen stack. The bottom entry is always a top-level drawer screen; channels and the player
 * stack above it. State is Compose snapshot state, so screens recompose when it changes.
 */
class AppNavigator(private val clock: () -> Long = System::currentTimeMillis) {
    private val stack = mutableStateListOf<Screen>(Screen.Top(Destination.Home))
    private var lastBackOnHomeMillis: Long? = null

    val current: Screen get() = stack.last()

    /** Set by [openSearch]; the Search screen reads it once and clears it. */
    var pendingSearchQuery: String? by mutableStateOf(null)

    /**
     * The playlist queue whose video was left with Back and keeps playing in a bar on the playlist page below it;
     * null whenever no such bar exists.
     */
    var minimizedQueue: PlaybackQueue? by mutableStateOf(null)
        private set

    fun openDestination(destination: Destination) {
        minimizedQueue = null
        stack.clear()
        stack.add(Screen.Top(destination))
    }

    fun openChannel(channelUrl: String) {
        minimizedQueue = null
        stack.add(Screen.Channel(channelUrl))
    }

    /** True while a channel page sits directly on top of the playing video, which then shows as a bar. */
    val isChannelOverPlayer: Boolean
        get() = current is Screen.Channel && stack.size >= 2 && stack[stack.lastIndex - 1] is Screen.Player

    /** True while the playing video shows as a bar: over a channel page opened from it, or on its playlist page. */
    val isBarOverPage: Boolean
        get() = isChannelOverPlayer || isPlaylistBarVisible

    private val isPlaylistBarVisible: Boolean
        get() {
            val queue = minimizedQueue ?: return false
            val page = current
            return page is Screen.Playlist && page.source == queue.source
        }

    /**
     * Brings the bar's video back to full screen: from a channel page that is Back, from a playlist page the
     * queue's current video is opened again (the running engine is reused).
     */
    fun openBarFullScreen() {
        val queue = minimizedQueue
        if (queue != null) {
            playQueue(queue)
        } else {
            back()
        }
    }

    /**
     * Removes the bar after its video was stopped. Under a channel page the player entry goes too, or Back
     * from the channel would open that video again.
     */
    fun closeBar() {
        if (isChannelOverPlayer) {
            stack.removeAt(stack.lastIndex - 1)
        }
        minimizedQueue = null
    }

    /** Moves the bar to the next entry of its queue; false when the queue was at its end. */
    fun advanceMinimizedQueue(): Boolean {
        val next = minimizedQueue?.next() ?: return false
        minimizedQueue = next
        return true
    }

    fun openVideo(videoUrl: String) {
        minimizedQueue = null
        // Picking a related video replaces the one playing, so Back leaves the player in one step.
        if (current is Screen.Player) {
            stack.removeAt(stack.lastIndex)
        }
        stack.add(Screen.Player(videoUrl))
    }

    fun openPlaylist(source: PlaylistSource) {
        minimizedQueue = null
        stack.add(Screen.Playlist(source))
    }

    /** Plays [queue]'s current video; the next entry replaces the player, so Back still reaches the playlist. */
    fun playQueue(queue: PlaybackQueue) {
        minimizedQueue = null
        if (current is Screen.Player) {
            stack.removeAt(stack.lastIndex)
        }
        stack.add(Screen.Player(queue.currentUrl, queue))
    }

    fun openSearch(query: String) {
        pendingSearchQuery = query
        openDestination(Destination.Search)
    }

    /** Returns false when the app should exit (second Back on Home within the window). */
    fun back(): Boolean {
        val top = stack.last()
        // A single video ends at Home; only a playlist video goes back to its playlist, still playing.
        if (top is Screen.Player && top.queue == null) {
            openDestination(Destination.Home)
            return true
        }
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            // A playlist video keeps playing in a bar on the playlist page it was started from.
            val leftQueue = (top as? Screen.Player)?.queue
            val page = current
            val isReturningToItsPlaylist =
                leftQueue != null && page is Screen.Playlist && page.source == leftQueue.source
            minimizedQueue = if (isReturningToItsPlaylist) leftQueue else null
            return true
        }
        if (top is Screen.Top && top.destination != Destination.Home) {
            openDestination(Destination.Home)
            return true
        }
        val now = clock()
        val previous = lastBackOnHomeMillis
        if (previous != null && now - previous <= DOUBLE_BACK_WINDOW_MILLIS) {
            return false
        }
        lastBackOnHomeMillis = now
        return true
    }

    private companion object {
        const val DOUBLE_BACK_WINDOW_MILLIS = 2_000L
    }
}
