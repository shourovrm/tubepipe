package org.tubepipe.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import org.tubepipe.core.ui.theme.TubepipeSpacing

private const val GRID_COLUMN_COUNT = 4

/**
 * The 4-column grid behind [VideoGrid], for screens whose items are not all videos (search shows
 * videos, channels and playlists together). [onNearEnd] fires when a card in the last row is composed.
 */
@Composable
fun <T> ContentGrid(
    items: List<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    onNearEnd: (() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMN_COUNT),
        horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
        contentPadding = PaddingValues(
            horizontal = TubepipeSpacing.SafeMarginHorizontal,
            vertical = TubepipeSpacing.SafeMarginVertical,
        ),
        modifier = modifier,
    ) {
        itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
            if (onNearEnd != null && index >= items.size - GRID_COLUMN_COUNT) {
                LaunchedEffect(items.size) { onNearEnd() }
            }
            itemContent(item)
        }
    }
}
