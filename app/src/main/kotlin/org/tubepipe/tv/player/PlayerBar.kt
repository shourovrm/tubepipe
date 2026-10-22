package org.tubepipe.tv.player

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.navigation.AppNavigator

private val BarHeight = 72.dp
private val BarVideoWidth = 112.dp
private val BarVideoHeight = 63.dp
private val BarButtonSize = 48.dp
private val BarRadius = 8.dp
private val ProgressLineHeight = 3.dp

/**
 * The strip along the bottom of a channel page that opened over a playing video: a small live picture, the
 * title with a thin progress line, a Play/Pause button and an "open full screen" button. Centre on the picture
 * or the full-screen button returns to the player; the navigator's Back does the same from the page above.
 * Draws nothing unless the video is in [PlayerMode.Bar].
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerBar(container: AppContainer, navigator: AppNavigator, modifier: Modifier = Modifier) {
    val controller = container.playback
    val nowPlaying by controller.state.collectAsStateWithLifecycle()
    val engine by controller.engine.collectAsStateWithLifecycle()
    val current = nowPlaying?.takeIf { it.mode == PlayerMode.Bar } ?: return
    val tubepipeEngine = engine as? TubepipePlayerEngine ?: return
    val viewModel = rememberPlayerViewModel(current.videoUrl, container)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = tubepipeEngine.snapshot
    val videoDescription = stringResource(R.string.bar_video_description)
    // Back from the channel page pops to the player, which takes the same running engine back to full screen.
    val openFullScreen = { navigator.back() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TubepipeColors.Surface)
            .padding(horizontal = TubepipeSpacing.SafeMarginHorizontal),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
            modifier = Modifier.fillMaxWidth().height(BarHeight),
        ) {
            Surface(
                onClick = { openFullScreen() },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(BarRadius)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TubepipeColors.Background,
                    focusedContainerColor = TubepipeColors.Background,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = barFocusBorder(RoundedCornerShape(BarRadius)),
                modifier = Modifier
                    .size(width = BarVideoWidth, height = BarVideoHeight)
                    .semantics { contentDescription = videoDescription },
            ) {
                PlayerSurface(
                    player = tubepipeEngine.player?.exoPlayer,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = state.details?.video?.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = TubepipeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BarButton(
                description = stringResource(if (snapshot.playWhenReady) R.string.bar_pause else R.string.bar_play),
                onClick = controller::togglePause,
            ) {
                Icon(
                    imageVector = if (snapshot.playWhenReady) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(BarButtonSize / 2),
                )
            }
            BarButton(description = stringResource(R.string.bar_full_screen), onClick = { openFullScreen() }) {
                Icon(
                    imageVector = PlayerIcons.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(BarButtonSize / 2),
                )
            }
        }
        BarProgress(snapshot)
    }
}

@Composable
private fun BarButton(description: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TubepipeColors.SurfaceVariant,
            focusedContainerColor = TubepipeColors.SurfaceVariant,
            contentColor = TubepipeColors.OnSurface,
            focusedContentColor = TubepipeColors.OnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = barFocusBorder(CircleShape),
        modifier = Modifier.size(BarButtonSize).semantics { contentDescription = description },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun barFocusBorder(shape: Shape) = ClickableSurfaceDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder), shape = shape),
)

/** The thin red line along the bottom edge of the bar. */
@Composable
private fun BarProgress(snapshot: PlaybackSnapshot) {
    val fraction = if (snapshot.durationMillis > 0) {
        (snapshot.positionMillis.toFloat() / snapshot.durationMillis).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(modifier = Modifier.fillMaxWidth().height(ProgressLineHeight).background(TubepipeColors.SurfaceVariant)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(TubepipeColors.Primary),
        )
    }
}
