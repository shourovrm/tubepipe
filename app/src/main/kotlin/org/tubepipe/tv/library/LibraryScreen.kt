package org.tubepipe.tv.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.database.PlaylistEntity
import org.tubepipe.core.database.SavedSearchEntity
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.EmptyView
import org.tubepipe.core.ui.component.MediaRow
import org.tubepipe.core.ui.component.VideoGrid
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.common.VideoActionsDialog
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.navigation.AppNavigator

@Composable
fun LibraryScreen(container: AppContainer, navigator: AppNavigator) {
    val viewModel: LibraryViewModel = viewModel {
        LibraryViewModel(
            history = container.database.historyDao(),
            playlists = container.database.playlistDao(),
            searches = container.database.searchDao(),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionsVideo by remember { mutableStateOf<VideoItem?>(null) }

    val openedPlaylistId = state.openedPlaylistId
    if (openedPlaylistId != null) {
        OpenedPlaylist(
            videos = state.openedPlaylistVideos,
            onVideoClick = { video -> navigator.openVideo(video.url) },
            onVideoLongClick = { video -> actionsVideo = video },
            onBack = viewModel::closePlaylist,
        )
    } else {
        LibrarySections(
            state = state,
            viewModel = viewModel,
            navigator = navigator,
            onVideoLongClick = { video -> actionsVideo = video },
        )
    }

    val selectedVideo = actionsVideo
    if (selectedVideo != null) {
        VideoActionsDialog(
            video = selectedVideo,
            playlists = container.database.playlistDao(),
            onPlay = { navigator.openVideo(selectedVideo.url) },
            onOpenChannel = navigator::openChannel,
            onDismiss = { actionsVideo = null },
        )
    }
}

@Composable
private fun OpenedPlaylist(
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    if (videos.isEmpty()) {
        EmptyView(message = stringResource(R.string.library_playlist_empty))
        return
    }
    // toCardModel is @Composable (it formats plurals), so the models are built here, outside the grid's lambdas.
    val cardModelByUrl = videos.associate { video -> video.url to video.toCardModel() }
    VideoGrid(
        items = videos,
        itemKey = { video -> video.url },
        cardModel = { video -> cardModelByUrl.getValue(video.url) },
        onItemClick = onVideoClick,
        onItemLongClick = onVideoLongClick,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun LibrarySections(
    state: LibraryState,
    viewModel: LibraryViewModel,
    navigator: AppNavigator,
    onVideoLongClick: (VideoItem) -> Unit,
) {
    var confirmingClearHistory by rememberSaveable { mutableStateOf(false) }
    var creatingPlaylist by rememberSaveable { mutableStateOf(false) }
    var playlistForDeletion by remember { mutableStateOf<PlaylistEntity?>(null) }
    var searchForActions by remember { mutableStateOf<SavedSearchEntity?>(null) }
    var searchForDeletion by remember { mutableStateOf<SavedSearchEntity?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = TubepipeSpacing.SafeMarginVertical),
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "history") {
            HistorySection(
                history = state.history,
                onVideoClick = { video -> navigator.openVideo(video.url) },
                onVideoLongClick = onVideoLongClick,
                onClearHistory = { confirmingClearHistory = true },
            )
        }
        item(key = "playlists-title") { SectionTitle(stringResource(R.string.library_playlists)) }
        items(state.playlists, key = { playlist -> "playlist-${playlist.id}" }) { playlist ->
            val isLiked = playlist.id == PlaylistEntity.LIKED_ID
            ListRow(
                headline = if (isLiked) stringResource(R.string.library_liked_playlist) else playlist.name,
                onClick = { viewModel.openPlaylist(playlist.id) },
                // Liked is built in, so it has no long-press menu.
                onLongClick = if (isLiked) null else ({ playlistForDeletion = playlist }),
            )
        }
        item(key = "new-playlist") {
            ListRow(
                headline = stringResource(R.string.library_new_playlist),
                onClick = { creatingPlaylist = true },
            )
        }
        item(key = "searches-title") { SectionTitle(stringResource(R.string.library_saved_searches)) }
        if (state.savedSearches.isEmpty()) {
            item(key = "searches-empty") {
                Text(
                    text = stringResource(R.string.library_saved_searches_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TubepipeColors.OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TubepipeSpacing.SafeMarginHorizontal),
                )
            }
        }
        items(state.savedSearches, key = { search -> "search-${search.query}" }) { search ->
            ListRow(
                headline = search.query,
                supporting = if (search.pinned) stringResource(R.string.library_pinned_marker) else null,
                onClick = { navigator.openSearch(search.query) },
                onLongClick = { searchForActions = search },
            )
        }
    }

    if (confirmingClearHistory) {
        ChoiceDialog(
            title = stringResource(R.string.library_clear_history_title),
            message = stringResource(R.string.library_clear_history_message),
            choices = listOf(
                Choice(stringResource(R.string.library_cancel)) {},
                Choice(stringResource(R.string.library_confirm_clear)) { viewModel.clearHistory() },
            ),
            onDismiss = { confirmingClearHistory = false },
        )
    }
    if (creatingPlaylist) {
        NewPlaylistDialog(
            onCreate = viewModel::createPlaylist,
            onDismiss = { creatingPlaylist = false },
        )
    }
    playlistForDeletion?.let { playlist ->
        ChoiceDialog(
            title = playlist.name,
            choices = listOf(
                Choice(stringResource(R.string.library_delete_playlist)) { viewModel.deletePlaylist(playlist.id) },
                Choice(stringResource(R.string.library_cancel)) {},
            ),
            onDismiss = { playlistForDeletion = null },
        )
    }
    searchForActions?.let { search ->
        val pinChoice = if (search.pinned) {
            Choice(stringResource(R.string.library_unpin_search)) { viewModel.unpinSavedSearch() }
        } else {
            Choice(stringResource(R.string.library_pin_search)) { viewModel.pinSavedSearch(search.query) }
        }
        ChoiceDialog(
            title = search.query,
            choices = listOf(
                Choice(stringResource(R.string.library_open_search)) { navigator.openSearch(search.query) },
                pinChoice,
                Choice(stringResource(R.string.library_delete_search)) { searchForDeletion = search },
            ),
            onDismiss = { searchForActions = null },
        )
    }
    searchForDeletion?.let { search ->
        ConfirmDialog(
            title = stringResource(R.string.library_delete_search_title),
            message = stringResource(R.string.library_delete_search_message, search.query),
            confirmLabel = stringResource(R.string.library_confirm_delete),
            onConfirm = { viewModel.deleteSavedSearch(search.query) },
            onDismiss = { searchForDeletion = null },
        )
    }
}

@Composable
private fun HistorySection(
    history: List<HistoryEntry>,
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isEmpty()) {
        Column {
            SectionTitle(stringResource(R.string.library_history))
            Text(
                text = stringResource(R.string.library_history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = TubepipeColors.OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = TubepipeSpacing.SafeMarginHorizontal),
            )
        }
        return
    }
    val cardModelByUrl = history.associate { entry ->
        entry.video.url to entry.video.toCardModel(entry.watchedFraction)
    }
    Column {
        MediaRow(
            title = stringResource(R.string.library_history),
            items = history,
            itemKey = { entry -> entry.video.url },
            cardModel = { entry -> cardModelByUrl.getValue(entry.video.url) },
            onItemClick = { entry -> onVideoClick(entry.video) },
            onItemLongClick = { entry -> onVideoLongClick(entry.video) },
        )
        Button(
            onClick = onClearHistory,
            modifier = Modifier.padding(horizontal = TubepipeSpacing.SafeMarginHorizontal),
        ) {
            Text(text = stringResource(R.string.library_clear_history))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = TubepipeColors.OnSurface,
        modifier = Modifier.padding(horizontal = TubepipeSpacing.SafeMarginHorizontal),
    )
}

@Composable
private fun ListRow(
    headline: String,
    onClick: () -> Unit,
    supporting: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = { Text(text = headline, maxLines = 1) },
        supportingContent = supporting?.let { text -> { Text(text = text) } },
        modifier = Modifier
            .padding(horizontal = TubepipeSpacing.SafeMarginHorizontal)
            .fillMaxWidth(),
    )
}

private class Choice(val label: String, val onChosen: () -> Unit)

/** A small dialog of buttons; focus starts on the first so one Centre press acts. */
@Composable
private fun ChoiceDialog(title: String, choices: List<Choice>, onDismiss: () -> Unit, message: String? = null) {
    val firstChoiceFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = TubepipeColors.OnSurface)
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TubepipeColors.OnSurfaceVariant,
                )
            }
            choices.forEachIndexed { index, choice ->
                Button(
                    onClick = {
                        onDismiss()
                        choice.onChosen()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstChoiceFocus) else Modifier),
                ) {
                    Text(text = choice.label)
                }
            }
        }
        LaunchedEffect(Unit) { firstChoiceFocus.requestFocus() }
    }
}

@Composable
private fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val canCreate = name.isNotBlank()
    val submit = {
        if (canCreate) {
            onCreate(name)
            onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(
                text = stringResource(R.string.library_new_playlist),
                style = MaterialTheme.typography.titleLarge,
                color = TubepipeColors.OnSurface,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TubepipeColors.SurfaceVariant, RoundedCornerShape(8.dp))
                    .border(
                        width = if (fieldFocused) 3.dp else 1.dp,
                        color = if (fieldFocused) TubepipeColors.FocusBorder else TubepipeColors.BorderVariant,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(16.dp),
            ) {
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_new_playlist_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TubepipeColors.OnSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TubepipeColors.OnSurface),
                    cursorBrush = SolidColor(TubepipeColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .onFocusChanged { fieldFocused = it.isFocused },
                )
            }
            Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.library_create))
            }
        }
        LaunchedEffect(Unit) { fieldFocus.requestFocus() }
    }
}

@Composable
private fun DialogSurface(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 3),
        modifier = Modifier
            .width(480.dp)
            .background(TubepipeColors.Surface, RoundedCornerShape(12.dp))
            .padding(TubepipeSpacing.Grid * 6),
    ) {
        content()
    }
}
