package org.tubepipe.tv.playlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.PlaceholderScreen
import org.tubepipe.tv.R
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.navigation.PlaylistSource

/** A playlist's videos with Play all; the search-and-playlists slice builds it. */
@Composable
fun PlaylistScreen(source: PlaylistSource, container: AppContainer, navigator: AppNavigator) {
    PlaceholderScreen(title = stringResource(R.string.placeholder_not_built))
}
