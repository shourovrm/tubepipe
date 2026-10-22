package org.tubepipe.tv.playlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.ContentGrid
import org.tubepipe.core.ui.component.EmptyView
import org.tubepipe.core.ui.component.ErrorView
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.component.VideoCard
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.common.VideoActionsDialog
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.navigation.PlaylistSource

/** A playlist's header, Play all and Shuffle buttons, and its videos; works for YouTube and Library playlists. */
@Composable
fun PlaylistScreen(source: PlaylistSource, container: AppContainer, navigator: AppNavigator) {
    val likedPlaylistTitle = stringResource(R.string.library_liked_playlist)
    // The key keeps two playlists in the back stack from sharing one view model.
    val viewModel = viewModel(key = source.toString()) {
        PlaylistViewModel(
            source = source,
            content = container.content,
            playlistDao = container.database.playlistDao(),
            likedPlaylistTitle = likedPlaylistTitle,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionsVideo by remember { mutableStateOf<VideoItem?>(null) }
    val playAllFocus = remember { FocusRequester() }
    val hasVideos = state.videos.isNotEmpty()
    val indexedVideos = remember(state.videos) { state.videos.withIndex().toList() }

    // Focus starts on Play all once the buttons exist, so one Centre press starts the playlist.
    LaunchedEffect(hasVideos) {
        if (hasVideos) {
            playAllFocus.requestFocus()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PlaylistHeader(
            state = state,
            source = source,
            showButtons = hasVideos,
            playAllFocus = playAllFocus,
            onPlayAll = { viewModel.queueStartingAt(0)?.let(navigator::playQueue) },
            onShuffle = { viewModel.shuffledQueue()?.let(navigator::playQueue) },
        )
        when {
            state.isLoading -> LoadingView()
            state.hasError -> ErrorView(
                message = stringResource(R.string.error_network),
                onRetry = viewModel::retry,
            )
            !hasVideos -> EmptyView(message = stringResource(R.string.playlist_empty))
            else -> ContentGrid(
                items = indexedVideos,
                // The index is part of the key because a playlist may hold the same video twice.
                itemKey = { indexed -> "${indexed.index}:${indexed.value.url}" },
                onNearEnd = if (state.canLoadMore) viewModel::loadMore else null,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { indexed ->
                VideoCard(
                    model = indexed.value.toCardModel(),
                    onClick = { viewModel.queueStartingAt(indexed.index)?.let(navigator::playQueue) },
                    onLongClick = { actionsVideo = indexed.value },
                )
            }
        }
    }

    actionsVideo?.let { video ->
        VideoActionsDialog(
            video = video,
            playlists = container.database.playlistDao(),
            onPlay = { navigator.openVideo(video.url) },
            onOpenChannel = navigator::openChannel,
            onDismiss = { actionsVideo = null },
        )
    }
}

@Composable
private fun PlaylistHeader(
    state: PlaylistState,
    source: PlaylistSource,
    showButtons: Boolean,
    playAllFocus: FocusRequester,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    // A focus group spanning the full width, so Up from any grid column lands on a button.
    Column(
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 2),
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .padding(
                horizontal = TubepipeSpacing.SafeMarginHorizontal,
                vertical = TubepipeSpacing.SafeMarginVertical,
            ),
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium,
            color = TubepipeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val metaLine = metaLineFor(state, source)
        if (metaLine.isNotEmpty()) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodyLarge,
                color = TubepipeColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showButtons) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
                modifier = Modifier.padding(top = TubepipeSpacing.Grid * 2),
            ) {
                PlaylistButton(
                    label = stringResource(R.string.playlist_play_all),
                    isPrimary = true,
                    onClick = onPlayAll,
                    modifier = Modifier.focusRequester(playAllFocus),
                )
                PlaylistButton(
                    label = stringResource(R.string.playlist_shuffle),
                    isPrimary = false,
                    onClick = onShuffle,
                )
            }
        }
    }
}

/** "Uploader · 24 videos": the owner for YouTube playlists, a fixed label for local ones, then the count. */
@Composable
private fun metaLineFor(state: PlaylistState, source: PlaylistSource): String {
    val parts = mutableListOf<String>()
    val owner = when (source) {
        is PlaylistSource.Remote -> state.ownerName
        is PlaylistSource.Local -> stringResource(R.string.playlist_local_owner)
    }
    if (!owner.isNullOrEmpty() && !state.isLoading) {
        parts += owner
    }
    val count = state.videoCount
    if (count != null) {
        parts += pluralStringResource(
            R.plurals.playlist_header_video_count,
            count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
    }
    return parts.joinToString(separator = " · ")
}

@Composable
private fun PlaylistButton(label: String, isPrimary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor = if (isPrimary) TubepipeColors.Primary else TubepipeColors.SurfaceVariant
    val contentColor = if (isPrimary) TubepipeColors.OnPrimary else TubepipeColors.OnSurface
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = containerColor,
            focusedContentColor = contentColor,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder)),
        ),
        modifier = modifier,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}
