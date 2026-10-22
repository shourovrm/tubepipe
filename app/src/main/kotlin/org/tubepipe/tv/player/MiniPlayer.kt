package org.tubepipe.tv.player

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.tubepipe.core.ui.component.TimeFormatting
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.navigation.AppNavigator

private val MiniVideoWidth = 320.dp
private val MiniVideoHeight = 180.dp
private val MiniRadius = 12.dp

/**
 * Bottom-right window over whatever screen is shown. Centre on the picture returns to full screen; Pause and
 * Stop are buttons, and the media Play/Pause key is handled by the activity.
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerWindow(
    videoUrl: String,
    engine: TubepipePlayerEngine,
    container: AppContainer,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
) {
    val controller = container.playback
    val viewModel = rememberPlayerViewModel(videoUrl, container)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = engine.snapshot
    val videoDescription = stringResource(R.string.mini_video_description)

    Column(
        modifier = modifier
            .padding(
                end = TubepipeSpacing.SafeMarginHorizontal,
                bottom = TubepipeSpacing.SafeMarginVertical,
            )
            .width(MiniVideoWidth)
            .background(TubepipeColors.Surface, RoundedCornerShape(MiniRadius)),
    ) {
        Surface(
            onClick = {
                controller.expand()
                navigator.openVideo(videoUrl)
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(topStart = MiniRadius, topEnd = MiniRadius)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TubepipeColors.Background,
                focusedContainerColor = TubepipeColors.Background,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder)),
            ),
            modifier = Modifier
                .size(width = MiniVideoWidth, height = MiniVideoHeight)
                .semantics { contentDescription = videoDescription },
        ) {
            val exoPlayer = engine.player?.exoPlayer
            PlayerSurface(
                player = exoPlayer,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.size(width = MiniVideoWidth, height = MiniVideoHeight),
            )
        }
        MiniProgress(snapshot)
        Column(
            modifier = Modifier.padding(horizontal = TubepipeSpacing.Grid * 3, vertical = TubepipeSpacing.Grid * 2),
            verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = state.details?.video?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TubepipeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = TimeFormatting.duration(snapshot.positionMillis / 1_000) + " / " +
                        TimeFormatting.duration(snapshot.durationMillis / 1_000),
                    style = MaterialTheme.typography.labelLarge,
                    color = TubepipeColors.OnSurfaceVariant,
                    modifier = Modifier.padding(start = TubepipeSpacing.Grid * 2),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 2)) {
                Button(onClick = controller::togglePause) {
                    Text(stringResource(if (snapshot.playWhenReady) R.string.player_pause else R.string.player_play))
                }
                Button(onClick = controller::stop) { Text(stringResource(R.string.mini_stop)) }
            }
        }
    }
}

@Composable
private fun MiniProgress(snapshot: PlaybackSnapshot) {
    val fraction = if (snapshot.durationMillis > 0) {
        (snapshot.positionMillis.toFloat() / snapshot.durationMillis).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(TubepipeColors.SurfaceVariant)) {
        Box(modifier = Modifier.fillMaxWidth(fraction).height(4.dp).background(TubepipeColors.Primary))
    }
}
