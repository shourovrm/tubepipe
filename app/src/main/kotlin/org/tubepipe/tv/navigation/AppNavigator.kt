package org.tubepipe.tv.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.tubepipe.tv.Destination

sealed interface Screen {
    data class Top(val destination: Destination) : Screen

    data class Channel(val channelUrl: String) : Screen

    data class Player(val videoUrl: String) : Screen
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

    fun openDestination(destination: Destination) {
        stack.clear()
        stack.add(Screen.Top(destination))
    }

    fun openChannel(channelUrl: String) {
        stack.add(Screen.Channel(channelUrl))
    }

    fun openVideo(videoUrl: String) {
        // Picking a related video replaces the one playing, so Back leaves the player in one step.
        if (current is Screen.Player) {
            stack.removeAt(stack.lastIndex)
        }
        stack.add(Screen.Player(videoUrl))
    }

    fun openSearch(query: String) {
        pendingSearchQuery = query
        openDestination(Destination.Search)
    }

    /** Returns false when the app should exit (second Back on Home within the window). */
    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        val top = current
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
