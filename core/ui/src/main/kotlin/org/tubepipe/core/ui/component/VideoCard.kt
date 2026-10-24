package org.tubepipe.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.tubepipe.core.ui.R
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing

/** What a video card shows; screens map their own models to this. */
data class VideoCardModel(
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val isLive: Boolean,
    val watchedFraction: Float = 0f,
    val isShort: Boolean = false,
)

/**
 * A 16:9 thumbnail card. Focus scales it to 1.1 and draws the 3dp red border
 * (docs/design-system.md); both run in the card's graphics layer, so focus never relayouts the row.
 */
@Composable
fun VideoCard(
    model: VideoCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val cardShape = RoundedCornerShape(TubepipeSpacing.CardRadius)
    Column(modifier = modifier.width(TubepipeSpacing.CardWidth)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = CardDefaults.shape(shape = cardShape),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder), shape = cardShape),
            ),
            colors = CardDefaults.colors(containerColor = TubepipeColors.Surface),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = model.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                CardBadge(model = model, modifier = Modifier.align(Alignment.BottomEnd))
                if (model.watchedFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(model.watchedFraction)
                            .height(3.dp)
                            .background(TubepipeColors.Primary),
                    )
                }
            }
        }
        Text(
            text = model.title,
            style = MaterialTheme.typography.titleMedium,
            color = TubepipeColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = TubepipeSpacing.Grid * 2),
        )
        Text(
            text = model.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TubepipeColors.OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CardBadge(model: VideoCardModel, modifier: Modifier) {
    val badgeShape = RoundedCornerShape(4.dp)
    val badgeModifier = modifier.padding(TubepipeSpacing.Grid)
    if (model.isLive) {
        Text(
            text = stringResource(R.string.badge_live),
            style = MaterialTheme.typography.labelLarge,
            color = TubepipeColors.OnPrimary,
            modifier = badgeModifier
                .background(TubepipeColors.Primary, badgeShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    } else if (model.durationSeconds != null || model.isShort) {
        // YouTube search gives no length for Shorts, so they get a "Shorts" badge in the same place instead.
        val badgeText = if (model.durationSeconds != null) {
            TimeFormatting.duration(model.durationSeconds)
        } else {
            stringResource(R.string.badge_shorts)
        }
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelLarge,
            color = TubepipeColors.OnSurface,
            modifier = badgeModifier
                .background(TubepipeColors.Scrim, badgeShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
