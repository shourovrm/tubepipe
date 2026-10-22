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

/**
 * The 4-column grid used by Trending, Search results and channel tabs. [onNearEnd] fires when a
 * card in the last row is composed, so the screen can load the next page before focus reaches it.
 */
@Composable
fun <T> VideoGrid(
    items: List<T>,
    itemKey: (T) -> Any,
    cardModel: (T) -> VideoCardModel,
    onItemClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    onItemLongClick: ((T) -> Unit)? = null,
    onNearEnd: (() -> Unit)? = null,
) {
    val columnCount = 4
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
        contentPadding = PaddingValues(
            horizontal = TubepipeSpacing.SafeMarginHorizontal,
            vertical = TubepipeSpacing.SafeMarginVertical,
        ),
        modifier = modifier,
    ) {
        itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
            if (onNearEnd != null && index >= items.size - columnCount) {
                LaunchedEffect(items.size) { onNearEnd() }
            }
            val longClick = onItemLongClick
            VideoCard(
                model = cardModel(item),
                onClick = { onItemClick(item) },
                onLongClick = if (longClick != null) {
                    { longClick(item) }
                } else {
                    null
                },
            )
        }
    }
}
