package org.tubepipe.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing

/**
 * A titled, lazily composed row of video cards. [itemKey] must be stable (the video URL) so
 * focus and scroll position survive a refresh.
 */
@Composable
fun <T> MediaRow(
    title: String,
    items: List<T>,
    itemKey: (T) -> Any,
    cardModel: (T) -> VideoCardModel,
    onItemClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    onItemLongClick: ((T) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TubepipeColors.OnSurface,
            modifier = Modifier.padding(
                start = TubepipeSpacing.SafeMarginHorizontal,
                bottom = TubepipeSpacing.Grid * 3,
            ),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
            // Vertical room so the 1.1x focus scale is not clipped by the row bounds.
            contentPadding = PaddingValues(
                horizontal = TubepipeSpacing.SafeMarginHorizontal,
                vertical = TubepipeSpacing.Grid * 3,
            ),
        ) {
            itemsIndexed(items, key = { _, item -> itemKey(item) }) { _, item ->
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
}
