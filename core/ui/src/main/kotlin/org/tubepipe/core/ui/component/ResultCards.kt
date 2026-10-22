package org.tubepipe.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing

/** What a channel card shows; [subtitle] is the subscriber count already formatted, or empty when unknown. */
data class ChannelCardModel(val name: String, val subtitle: String, val avatarUrl: String?)

/** What a playlist card shows; [badgeText] is the "N videos" label, or null when the size is unknown. */
data class PlaylistCardModel(val title: String, val subtitle: String, val thumbnailUrl: String?, val badgeText: String?)

private val AvatarSize = 88.dp

/**
 * Same outer size as [VideoCard] (196dp wide, 16:9 face) so channels sit in the grid between
 * videos without breaking the rows; the round avatar replaces the thumbnail.
 */
@Composable
fun ChannelCard(model: ChannelCardModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cardShape = RoundedCornerShape(TubepipeSpacing.CardRadius)
    Column(modifier = modifier.width(TubepipeSpacing.CardWidth)) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(shape = cardShape),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder), shape = cardShape),
            ),
            colors = CardDefaults.colors(containerColor = TubepipeColors.Surface),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = model.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(TubepipeColors.SurfaceVariant),
                )
            }
        }
        Text(
            text = model.name,
            style = MaterialTheme.typography.titleMedium,
            color = TubepipeColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = TubepipeSpacing.Grid * 2),
        )
        Text(
            text = model.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TubepipeColors.OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A 16:9 thumbnail card with an "N videos" badge in the bottom-right corner, like [VideoCard]'s duration. */
@Composable
fun PlaylistCard(model: PlaylistCardModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cardShape = RoundedCornerShape(TubepipeSpacing.CardRadius)
    val badgeShape = RoundedCornerShape(4.dp)
    Column(modifier = modifier.width(TubepipeSpacing.CardWidth)) {
        Card(
            onClick = onClick,
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
                if (model.badgeText != null) {
                    Text(
                        text = model.badgeText,
                        style = MaterialTheme.typography.labelLarge,
                        color = TubepipeColors.OnSurface,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(TubepipeSpacing.Grid)
                            .background(TubepipeColors.Scrim, badgeShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
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
