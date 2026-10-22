package org.tubepipe.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import org.tubepipe.core.ui.theme.TubepipeTheme
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.navigation.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navigator = remember { AppNavigator() }
            TubepipeTheme {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    TubepipeApp(navigator = navigator, onExit = ::finish) { screen ->
                        when (screen) {
                            is Screen.Top -> PlaceholderScreen(stringResource(screen.destination.labelResource))
                            is Screen.Channel -> PlaceholderScreen(screen.channelUrl)
                            is Screen.Player -> PlaceholderScreen(screen.videoUrl)
                        }
                    }
                }
            }
        }
    }
}
