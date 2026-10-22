package org.tubepipe.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.MaterialTheme
import org.tubepipe.core.ui.component.LocalVideoCardFocusListener
import org.tubepipe.core.ui.component.VideoCardFocusListener
import org.tubepipe.core.ui.theme.TubepipeTheme
import org.tubepipe.tv.channel.ChannelScreen
import org.tubepipe.tv.home.HomeScreen
import org.tubepipe.tv.library.LibraryScreen
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.navigation.Screen
import org.tubepipe.tv.player.PlaybackHost
import org.tubepipe.tv.player.PlayerScreen
import org.tubepipe.tv.playlist.PlaylistScreen
import org.tubepipe.tv.search.SearchScreen
import org.tubepipe.tv.settings.SettingsScreen
import org.tubepipe.tv.subscriptions.SubscriptionsScreen
import org.tubepipe.tv.trending.TrendingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val container = (application as TubepipeApplication).container
            val navigator = container.navigator
            val cardFocusListener = remember(container) {
                VideoCardFocusListener(
                    onCardFocused = container.prefetchScheduler::onCardFocused,
                    onCardUnfocused = container.prefetchScheduler::cancelPending,
                )
            }
            CompositionLocalProvider(LocalVideoCardFocusListener provides cardFocusListener) {
                TubepipeTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .onPreviewKeyEvent { event -> handleMediaKey(event, container) },
                    ) {
                        TubepipeApp(container = container, onExit = ::finish) { screen ->
                            when (screen) {
                                is Screen.Top -> TopLevelScreen(screen.destination, container, navigator)
                                is Screen.Channel -> ChannelScreen(screen.channelUrl, container, navigator)
                                is Screen.Player -> PlayerScreen(screen.videoUrl, screen.queue, container, navigator)
                                is Screen.Playlist -> PlaylistScreen(screen.source, container, navigator)
                            }
                        }
                        PlaybackHost(container, navigator)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            // Nothing is on screen any more, so a still-playing video would only be heard.
            (application as TubepipeApplication).container.playback.stop()
        }
        super.onDestroy()
    }
}

/** The remote's Play/Pause keys work on the playing video from any screen, including while the channel bar is shown. */
private fun handleMediaKey(event: KeyEvent, container: AppContainer): Boolean {
    if (event.type != KeyEventType.KeyDown || container.playback.state.value == null) {
        return false
    }
    return when (event.key) {
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
            container.playback.togglePause()
            true
        }
        else -> false
    }
}

@Composable
private fun TopLevelScreen(destination: Destination, container: AppContainer, navigator: AppNavigator) {
    when (destination) {
        Destination.Home -> HomeScreen(container, navigator)
        Destination.Trending -> TrendingScreen(container, navigator)
        Destination.Subscriptions -> SubscriptionsScreen(container, navigator)
        Destination.Search -> SearchScreen(container, navigator)
        Destination.Library -> LibraryScreen(container, navigator)
        Destination.Settings -> SettingsScreen(container, navigator)
    }
}
