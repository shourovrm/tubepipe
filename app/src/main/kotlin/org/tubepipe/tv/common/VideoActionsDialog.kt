package org.tubepipe.tv.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.tubepipe.core.database.PlaylistDao
import org.tubepipe.core.database.PlaylistEntity
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.R

/**
 * The long-press menu on any video card (docs/decisions/architecture.md: channel, add to
 * playlist, like, play). Focus starts on the first entry so the remote can act at once.
 */
@Composable
fun VideoActionsDialog(
    video: VideoItem,
    playlists: PlaylistDao,
    onPlay: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var choosingPlaylist by remember { mutableStateOf(false) }
    val isLiked by remember(video.url) {
        playlists.observeContains(PlaylistEntity.LIKED_ID, video.url)
    }.collectAsState(initial = false)
    val allPlaylists by remember(choosingPlaylist) {
        if (choosingPlaylist) playlists.observePlaylists() else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val firstEntryFocus = remember(choosingPlaylist) { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid),
            modifier = Modifier
                .width(420.dp)
                .background(TubepipeColors.Surface, RoundedCornerShape(12.dp))
                .padding(TubepipeSpacing.Grid * 4),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = TubepipeColors.OnSurface,
                maxLines = 2,
                modifier = Modifier.padding(bottom = TubepipeSpacing.Grid * 2),
            )
            if (!choosingPlaylist) {
                MenuEntry(stringResource(R.string.action_play), Modifier.focusRequester(firstEntryFocus)) {
                    onDismiss()
                    onPlay()
                }
                val channelUrl = video.channelUrl
                if (channelUrl != null) {
                    MenuEntry(stringResource(R.string.action_open_channel)) {
                        onDismiss()
                        onOpenChannel(channelUrl)
                    }
                }
                val likeLabel = if (isLiked) R.string.action_unlike else R.string.action_like
                MenuEntry(stringResource(likeLabel)) {
                    scope.launch {
                        if (isLiked) {
                            playlists.removeItem(PlaylistEntity.LIKED_ID, video.url)
                        } else {
                            playlists.addItem(PlaylistEntity.LIKED_ID, video.toPlaylistVideo())
                        }
                        onDismiss()
                    }
                }
                MenuEntry(stringResource(R.string.action_add_to_playlist)) { choosingPlaylist = true }
            } else {
                val userPlaylists = allPlaylists.filter { it.id != PlaylistEntity.LIKED_ID }
                if (userPlaylists.isEmpty()) {
                    MenuEntry(stringResource(R.string.action_no_playlists), Modifier.focusRequester(firstEntryFocus)) {
                        choosingPlaylist = false
                    }
                }
                userPlaylists.forEachIndexed { index, playlist ->
                    val modifier = if (index == 0) Modifier.focusRequester(firstEntryFocus) else Modifier
                    MenuEntry(playlist.name, modifier) {
                        scope.launch {
                            playlists.addItem(playlist.id, video.toPlaylistVideo())
                            onDismiss()
                        }
                    }
                }
            }
        }
        // Inside the dialog window, so the entries are composed before focus is requested.
        LaunchedEffect(choosingPlaylist, allPlaylists.size) {
            firstEntryFocus.requestFocus()
        }
    }
}

@Composable
private fun MenuEntry(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(text = label) },
        modifier = modifier,
    )
}
