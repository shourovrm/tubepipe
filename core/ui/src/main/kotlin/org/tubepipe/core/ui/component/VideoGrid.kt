package org.tubepipe.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    ContentGrid(items = items, itemKey = itemKey, modifier = modifier, onNearEnd = onNearEnd) { item ->
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
