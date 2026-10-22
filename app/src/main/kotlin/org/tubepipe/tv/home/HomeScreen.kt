package org.tubepipe.tv.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.EmptyView
import org.tubepipe.core.ui.component.ErrorView
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.component.MediaRow
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.common.VideoActionsDialog
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.data.HomeSection
import org.tubepipe.tv.navigation.AppNavigator

@Composable
fun HomeScreen(container: AppContainer, navigator: AppNavigator) {
    val context = LocalContext.current
    val viewModel = viewModel {
        HomeViewModel(
            content = container.content,
            history = container.database.historyDao(),
            subscriptions = container.database.subscriptionDao(),
            settings = container.settings.settings,
            recommendationStore = FileRecommendationStore(File(context.filesDir, "recommended.json")),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var videoWithOpenMenu by remember { mutableStateOf<VideoItem?>(null) }

    when {
        state.rows.isNotEmpty() -> HomeRows(
            rows = state.rows,
            onVideoClick = { video -> navigator.openVideo(video.url) },
            onVideoLongClick = { video -> videoWithOpenMenu = video },
        )
        state.isLoading -> LoadingView()
        state.hasError -> ErrorView(message = stringResource(R.string.error_network), onRetry = viewModel::refresh)
        else -> EmptyView(message = stringResource(R.string.home_empty))
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
private fun HomeRows(rows: List<HomeRow>, onVideoClick: (VideoItem) -> Unit, onVideoLongClick: (VideoItem) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = TubepipeSpacing.SafeMarginVertical)) {
        items(rows, key = { row -> row.section.name }) { row ->
            // MediaRow's cardModel lambda is not composable, but toCardModel needs string resources.
            val cardModelByUrl = row.items.associate { homeVideo ->
                homeVideo.video.url to homeVideo.video.toCardModel(homeVideo.watchedFraction)
            }
            MediaRow(
                title = rowTitle(row),
                items = row.items,
                itemKey = { homeVideo -> homeVideo.video.url },
                cardModel = { homeVideo -> cardModelByUrl.getValue(homeVideo.video.url) },
                onItemClick = { homeVideo -> onVideoClick(homeVideo.video) },
                onItemLongClick = { homeVideo -> onVideoLongClick(homeVideo.video) },
            )
        }
    }
}

@Composable
private fun rowTitle(row: HomeRow): String {
    val seedTitle = row.seedTitle.orEmpty()
    return when (row.section) {
        HomeSection.ContinueWatching -> stringResource(R.string.home_continue_watching)
        HomeSection.Recommended -> stringResource(R.string.home_recommended)
        HomeSection.Mix -> stringResource(R.string.home_mix_for, seedTitle)
        HomeSection.NewFromSubscriptions -> stringResource(R.string.home_new_from_subscriptions)
        HomeSection.TrendingMusic -> stringResource(R.string.home_trending_music)
        HomeSection.TrendingMovies -> stringResource(R.string.home_trending_movies)
    }
}
