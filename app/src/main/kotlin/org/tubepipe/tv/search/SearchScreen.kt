package org.tubepipe.tv.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.EmptyView
import org.tubepipe.core.ui.component.ErrorView
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.component.VideoGrid
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.common.VideoActionsDialog
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.navigation.AppNavigator

private val SearchFieldShape = RoundedCornerShape(12.dp)

@Composable
fun SearchScreen(container: AppContainer, navigator: AppNavigator) {
    val viewModel = viewModel {
        SearchViewModel(container.content, container.database.searchDao())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionsVideo by remember { mutableStateOf<VideoItem?>(null) }
    // toCardModel needs composition (plural strings) but VideoGrid asks for models outside it, so build them here.
    val cardModels = state.results.associate { video -> video.url to video.toCardModel() }

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
                modifier = Modifier.weight(1f),
            )
            if (state.submittedQuery != null && state.results.isNotEmpty()) {
                SaveSearchButton(isSaved = state.isCurrentSearchSaved, onClick = viewModel::saveCurrentSearch)
            }
        }

        val bodyModifier = Modifier.weight(1f).fillMaxWidth()
        val isTypingNewQuery = state.query.isNotBlank() && state.query != state.submittedQuery
        when {
            isTypingNewQuery && state.suggestions.isNotEmpty() -> QueryList(
                title = stringResource(R.string.search_suggestions_title),
                queries = state.suggestions,
                onQueryClick = viewModel::submit,
                modifier = bodyModifier,
            )
            state.query.isBlank() && state.recentSearches.isNotEmpty() -> QueryList(
                title = stringResource(R.string.search_recent_title),
                queries = state.recentSearches,
                onQueryClick = viewModel::submit,
                modifier = bodyModifier,
            )
            state.submittedQuery == null -> EmptyView(
                message = stringResource(R.string.search_prompt),
                modifier = bodyModifier,
            )
            state.isLoading -> LoadingView(modifier = bodyModifier)
            state.hasError -> ErrorView(
                message = stringResource(R.string.error_network),
                onRetry = viewModel::retry,
                modifier = bodyModifier,
            )
            state.results.isEmpty() -> EmptyView(
                message = stringResource(R.string.search_no_results),
                modifier = bodyModifier,
            )
            else -> VideoGrid(
                items = state.results,
                itemKey = { video -> video.url },
                cardModel = { video -> cardModels.getValue(video.url) },
                onItemClick = { video -> navigator.openVideo(video.url) },
                onItemLongClick = { video -> actionsVideo = video },
                onNearEnd = if (state.canLoadMore) viewModel::loadMore else null,
                modifier = bodyModifier,
            )
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
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
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
