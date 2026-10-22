package org.tubepipe.tv.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import org.tubepipe.core.ui.theme.TubepipeColors

/** Round channel picture; the grey disc stays visible while the image loads or when the channel has none. */
@Composable
internal fun ChannelAvatar(avatarUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(TubepipeColors.SurfaceVariant)) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size),
        )
    }
}
