package org.tubepipe.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import org.tubepipe.core.ui.theme.TubepipeTheme
import org.tubepipe.tv.channel.ChannelScreen
import org.tubepipe.tv.home.HomeScreen
import org.tubepipe.tv.library.LibraryScreen
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.navigation.Screen
import org.tubepipe.tv.player.PlayerScreen
import org.tubepipe.tv.search.SearchScreen
import org.tubepipe.tv.settings.SettingsScreen
import org.tubepipe.tv.subscriptions.SubscriptionsScreen
import org.tubepipe.tv.trending.TrendingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navigator = remember { AppNavigator() }
            val container = (application as TubepipeApplication).container
            TubepipeTheme {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    TubepipeApp(navigator = navigator, onExit = ::finish) { screen ->
                        when (screen) {
                            is Screen.Top -> TopLevelScreen(screen.destination, container, navigator)
                            is Screen.Channel -> ChannelScreen(screen.channelUrl, container, navigator)
                            is Screen.Player -> PlayerScreen(screen.videoUrl, container, navigator)
                        }
                    }
                }
            }
        }
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
