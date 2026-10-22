package org.tubepipe.tv.trending

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import org.tubepipe.core.service.TrendingKind
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.EmptyView
import org.tubepipe.core.ui.component.ErrorView
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.component.VideoGrid
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.common.VideoActionsDialog
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.navigation.AppNavigator

@Composable
fun TrendingScreen(container: AppContainer, navigator: AppNavigator) {
    val viewModel = viewModel { TrendingViewModel(container.content, container.settings.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var videoWithOpenMenu by remember { mutableStateOf<VideoItem?>(null) }

    Column(modifier = Modifier.padding(top = TubepipeSpacing.SafeMarginVertical)) {
        TrendingTabs(
            tabs = state.tabs,
            selected = state.selected,
            onTabFocused = viewModel::select,
        )
        when {
            state.isLoading -> LoadingView()
            state.hasError -> ErrorView(message = stringResource(R.string.error_network), onRetry = viewModel::retry)
            state.videos.isEmpty() -> EmptyView(message = stringResource(R.string.trending_empty))
            else -> TrendingGrid(
                videos = state.videos,
                onVideoClick = { video -> navigator.openVideo(video.url) },
                onVideoLongClick = { video -> videoWithOpenMenu = video },
            )
        }
    }

    val menuVideo = videoWithOpenMenu
    if (menuVideo != null) {
        VideoActionsDialog(
            video = menuVideo,
            playlists = container.database.playlistDao(),
            onPlay = { navigator.openVideo(menuVideo.url) },
            onOpenChannel = navigator::openChannel,
            onDismiss = { videoWithOpenMenu = null },
        )
    }
}

@Composable
private fun TrendingGrid(
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit,
) {
    // VideoGrid's cardModel lambda is not composable, but toCardModel needs string resources.
    val cardModelByUrl = videos.associate { video -> video.url to video.toCardModel() }
    VideoGrid(
        items = videos,
        itemKey = { video -> video.url },
        cardModel = { video -> cardModelByUrl.getValue(video.url) },
        onItemClick = onVideoClick,
        onItemLongClick = onVideoLongClick,
    )
}

@Composable
private fun TrendingTabs(tabs: List<TrendingKind>, selected: TrendingKind?, onTabFocused: (TrendingKind) -> Unit) {
    if (tabs.isEmpty()) {
        return
    }
    TabRow(
        selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
        modifier = Modifier.padding(horizontal = TubepipeSpacing.SafeMarginHorizontal),
    ) {
        tabs.forEach { kind ->
            // Focusing a tab selects it, so browsing categories needs no extra OK press;
            // OK on a tab selects it too, for remotes that only report clicks.
            Tab(
                selected = kind == selected,
                onFocus = { onTabFocused(kind) },
                onClick = { onTabFocused(kind) },
            ) {
                Text(
                    text = stringResource(tabLabel(kind)),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun tabLabel(kind: TrendingKind): Int = when (kind) {
    TrendingKind.Music -> R.string.trending_tab_music
    TrendingKind.MoviesAndShows -> R.string.trending_tab_movies
    TrendingKind.Gaming -> R.string.trending_tab_gaming
    TrendingKind.News -> R.string.trending_tab_news
}
