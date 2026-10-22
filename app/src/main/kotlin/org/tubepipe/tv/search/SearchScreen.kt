package org.tubepipe.tv.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.service.ChannelSummary
import org.tubepipe.core.service.PlaylistSummary
import org.tubepipe.core.service.SearchResult
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.ChannelCard
import org.tubepipe.core.ui.component.ChannelCardModel
import org.tubepipe.core.ui.component.ContentGrid
import org.tubepipe.core.ui.component.EmptyView
import org.tubepipe.core.ui.component.ErrorView
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.component.PlaylistCard
import org.tubepipe.core.ui.component.PlaylistCardModel
import org.tubepipe.core.ui.component.VideoCard
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.common.CountFormatting
import org.tubepipe.tv.common.VideoActionsDialog
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.library.ConfirmDialog
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.navigation.PlaylistSource

private val SearchFieldShape = RoundedCornerShape(12.dp)

@Composable
fun SearchScreen(container: AppContainer, navigator: AppNavigator) {
    val viewModel = viewModel {
        SearchViewModel(
            content = container.content,
            searchDao = container.database.searchDao(),
            settings = container.settings.settings,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionsVideo by remember { mutableStateOf<VideoItem?>(null) }
    var confirmingSave by remember { mutableStateOf(false) }

    // One requester serves whichever list the body shows (suggestions, recent searches or results),
    // so Down from the field always lands on what is on screen.
    val bodyFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val bodyContent = bodyContentFor(state)
    val isShowingResults = bodyContent == SearchBodyContent.Results

    // Once per submitted query, when its first results arrive: hide the keyboard and put focus on
    // the first card, so one Enter takes the viewer straight to the top result.
    LaunchedEffect(state.submittedQuery, isShowingResults) {
        if (isShowingResults) {
            keyboardController?.hide()
            bodyFocusRequester.requestFocus()
        }
    }

    // A query handed over from another screen (a saved search, a Home section) is run once, then cleared.
    LaunchedEffect(Unit) {
        val pendingQuery = navigator.pendingSearchQuery
        if (pendingQuery != null) {
            viewModel.submit(pendingQuery)
            navigator.pendingSearchQuery = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = TubepipeSpacing.SafeMarginHorizontal,
                    vertical = TubepipeSpacing.SafeMarginVertical,
                ),
        ) {
            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSubmit = { viewModel.submit(state.query) },
                downFocusTarget = if (bodyContent.hasFocusableItems) bodyFocusRequester else FocusRequester.Default,
                modifier = Modifier.weight(1f),
            )
            if (state.submittedQuery != null && state.items.isNotEmpty()) {
                SaveSearchButton(isSaved = state.isCurrentSearchSaved, onClick = { confirmingSave = true })
            }
        }

        val bodyModifier = Modifier.weight(1f).fillMaxWidth()
        // The focus group lets the requester land on the first item of the list or grid.
        val focusableBodyModifier = bodyModifier.focusRequester(bodyFocusRequester).focusGroup()
        when (bodyContent) {
            SearchBodyContent.Suggestions -> QueryList(
                title = stringResource(R.string.search_suggestions_title),
                queries = state.suggestions,
                onQueryClick = viewModel::submit,
                modifier = focusableBodyModifier,
            )
            SearchBodyContent.RecentSearches -> QueryList(
                title = stringResource(R.string.search_recent_title),
                queries = state.recentSearches,
                onQueryClick = viewModel::submit,
                modifier = focusableBodyModifier,
            )
            SearchBodyContent.Prompt -> EmptyView(
                message = stringResource(R.string.search_prompt),
                modifier = bodyModifier,
            )
            SearchBodyContent.Loading -> LoadingView(modifier = bodyModifier)
            SearchBodyContent.Error -> ErrorView(
                message = stringResource(R.string.error_network),
                onRetry = viewModel::retry,
                modifier = bodyModifier,
            )
            SearchBodyContent.NoResults -> EmptyView(
                message = stringResource(R.string.search_no_results),
                modifier = bodyModifier,
            )
            SearchBodyContent.Results -> Box(modifier = focusableBodyModifier) {
                ContentGrid(
                    items = state.items,
                    itemKey = ::searchResultKey,
                    onNearEnd = if (state.canLoadMore) viewModel::loadMore else null,
                    modifier = Modifier.fillMaxSize(),
                ) { result ->
                    SearchResultCard(result = result, navigator = navigator, onVideoLongClick = { actionsVideo = it })
                }
            }
        }
    }

    if (confirmingSave) {
        ConfirmDialog(
            title = stringResource(R.string.search_save_title),
            message = stringResource(R.string.search_save_message, state.submittedQuery.orEmpty()),
            confirmLabel = stringResource(R.string.search_save),
            onConfirm = viewModel::saveCurrentSearch,
            onDismiss = { confirmingSave = false },
        )
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

/** What the area under the search field shows; decides whether Down from the field has a target. */
private enum class SearchBodyContent(val hasFocusableItems: Boolean) {
    Suggestions(true),
    RecentSearches(true),
    Prompt(false),
    Loading(false),
    Error(false),
    NoResults(false),
    Results(true),
}

private fun bodyContentFor(state: SearchState): SearchBodyContent {
    val isTypingNewQuery = state.query.isNotBlank() && state.query != state.submittedQuery
    return when {
        isTypingNewQuery && state.suggestions.isNotEmpty() -> SearchBodyContent.Suggestions
        state.query.isBlank() && state.recentSearches.isNotEmpty() -> SearchBodyContent.RecentSearches
        state.submittedQuery == null -> SearchBodyContent.Prompt
        state.isLoading -> SearchBodyContent.Loading
        state.hasError -> SearchBodyContent.Error
        state.items.isEmpty() -> SearchBodyContent.NoResults
        else -> SearchBodyContent.Results
    }
}

private fun searchResultKey(result: SearchResult): String = when (result) {
    is SearchResult.Video -> "video:${result.video.url}"
    is SearchResult.Channel -> "channel:${result.channel.url}"
    is SearchResult.Playlist -> "playlist:${result.playlist.url}"
}

@Composable
private fun SearchResultCard(result: SearchResult, navigator: AppNavigator, onVideoLongClick: (VideoItem) -> Unit) {
    when (result) {
        is SearchResult.Video -> VideoCard(
            model = result.video.toCardModel(),
            onClick = { navigator.openVideo(result.video.url) },
            onLongClick = { onVideoLongClick(result.video) },
        )
        is SearchResult.Channel -> ChannelCard(
            model = result.channel.toCardModel(),
            onClick = { navigator.openChannel(result.channel.url) },
        )
        is SearchResult.Playlist -> PlaylistCard(
            model = result.playlist.toCardModel(),
            onClick = { navigator.openPlaylist(PlaylistSource.Remote(result.playlist.url)) },
        )
    }
}

@Composable
private fun ChannelSummary.toCardModel(): ChannelCardModel {
    val count = subscriberCount
    return ChannelCardModel(
        name = name,
        subtitle = if (count == null) {
            ""
        } else {
            pluralStringResource(
                R.plurals.channel_subscriber_count,
                count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                CountFormatting.compact(count),
            )
        },
        avatarUrl = avatarUrl,
    )
}

@Composable
private fun PlaylistSummary.toCardModel(): PlaylistCardModel {
    val count = videoCount
    return PlaylistCardModel(
        title = title,
        subtitle = uploaderName.orEmpty(),
        thumbnailUrl = thumbnailUrl,
        badgeText = if (count == null) {
            null
        } else {
            pluralStringResource(
                R.plurals.search_playlist_video_count,
                count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                CountFormatting.compact(count),
            )
        },
    )
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    downFocusTarget: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val borderColor = if (isFocused) TubepipeColors.FocusBorder else TubepipeColors.BorderVariant
    val borderWidth = if (isFocused) 3.dp else 1.dp
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = TubepipeColors.OnSurface),
        cursorBrush = SolidColor(TubepipeColors.Primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            // Down goes to the body's first item explicitly; left to itself it picks whichever card is nearest the wide field, often the second. Default is used while the body has nothing focusable.
            .focusProperties { down = downFocusTarget }
            // A remote has no tap, so Centre must open the keyboard itself.
            .onPreviewKeyEvent { keyEvent ->
                val isCentreRelease = keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyUp
                if (isCentreRelease) {
                    keyboardController?.show()
                }
                isCentreRelease
            },
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TubepipeColors.Surface, SearchFieldShape)
                    .border(BorderStroke(borderWidth, borderColor), SearchFieldShape)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_field_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = TubepipeColors.OnSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun SaveSearchButton(isSaved: Boolean, onClick: () -> Unit) {
    val labelRes = if (isSaved) R.string.search_saved else R.string.search_save
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = TubepipeColors.SurfaceVariant,
            contentColor = TubepipeColors.OnSurface,
            focusedContainerColor = TubepipeColors.SurfaceVariant,
            focusedContentColor = TubepipeColors.OnSurface,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder)),
        ),
    ) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun QueryList(
    title: String,
    queries: List<String>,
    onQueryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = TubepipeSpacing.SafeMarginHorizontal),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TubepipeColors.OnSurface,
            modifier = Modifier.padding(bottom = TubepipeSpacing.Grid * 3),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 2),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(queries, key = { query -> query }) { query ->
                ListItem(
                    selected = false,
                    onClick = { onQueryClick(query) },
                    colors = ListItemDefaults.colors(
                        containerColor = TubepipeColors.Surface,
                        contentColor = TubepipeColors.OnSurface,
                        focusedContainerColor = TubepipeColors.SurfaceVariant,
                        focusedContentColor = TubepipeColors.OnSurface,
                    ),
                    border = ListItemDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, TubepipeColors.FocusBorder)),
                    ),
                    headlineContent = { Text(text = query, style = MaterialTheme.typography.titleMedium) },
                )
            }
        }
    }
}
