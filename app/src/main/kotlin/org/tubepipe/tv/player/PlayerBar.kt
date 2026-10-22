package org.tubepipe.tv.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

/** Where the focus is in a [PlayerBar]; the page that shows the bar owns one and sends Back to it. */
class PlayerBarFocus {
    /** The bar's centre, the live picture. */
    val centre = FocusRequester()
    var hasFocus by mutableStateOf(false)
}

@Composable
fun rememberPlayerBarFocus(): PlayerBarFocus = remember { PlayerBarFocus() }

/**
 * Makes Back on the page above a bar move focus to the bar's centre first; once focus is in the bar, Back
 * does its usual page-level job. Handled on the key itself as well as through BackHandler, because on the box
 * the first Back otherwise only clears focus.
 */
@Composable
fun Modifier.sendBackToBar(isBarShown: Boolean, barFocus: PlayerBarFocus): Modifier {
    val isEnabled = isBarShown && !barFocus.hasFocus
    var isBackKeyUpPending by remember { mutableStateOf(false) }
    BackHandler(enabled = isEnabled) { barFocus.centre.requestFocus() }
    return onPreviewKeyEvent { event ->
        val isBackKey = event.key == Key.Back
        when {
            isEnabled && isBackKey && event.type == KeyEventType.KeyDown -> {
                barFocus.centre.requestFocus()
                isBackKeyUpPending = true
                true
            }
            isBackKey && event.type == KeyEventType.KeyUp && isBackKeyUpPending -> {
                isBackKeyUpPending = false
                true
            }
            else -> false
        }
    }
}

/**
 * The strip along the bottom of a page that sits over a playing video (a channel page opened from the video, or
 * the playlist page its queue came from): a close button, a small live picture, the title with a thin progress
 * line, a Play/Pause button and an "open full screen" button. Left from the picture reaches the close button,
 * which stops the video; Centre on the picture or the full-screen button returns to the player.
 * Draws nothing unless the video is in [PlayerMode.Bar].
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerBar(
    container: AppContainer,
    navigator: AppNavigator,
    barFocus: PlayerBarFocus,
    modifier: Modifier = Modifier,
) {
    val controller = container.playback
    val nowPlaying by controller.state.collectAsStateWithLifecycle()
    val engine by controller.engine.collectAsStateWithLifecycle()
    val current = nowPlaying?.takeIf { it.mode == PlayerMode.Bar } ?: return
    val tubepipeEngine = engine as? TubepipePlayerEngine ?: return
    val viewModel = rememberPlayerViewModel(current.videoUrl, container)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = tubepipeEngine.snapshot
    val videoDescription = stringResource(R.string.bar_video_description)
    val openFullScreen = navigator::openBarFullScreen
    val closeFocus = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState -> barFocus.hasFocus = focusState.hasFocus }
            .focusGroup()
            .background(TubepipeColors.Surface)
            .padding(horizontal = TubepipeSpacing.SafeMarginHorizontal),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
            modifier = Modifier.fillMaxWidth().height(BarHeight),
        ) {
            BarButton(
                description = stringResource(R.string.bar_close),
                onClick = {
                    controller.stop()
                    navigator.closeBar()
                },
                modifier = Modifier
                    .focusRequester(closeFocus)
                    .focusProperties { right = barFocus.centre },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(BarButtonSize / 2),
                )
            }
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
                    .focusRequester(barFocus.centre)
                    .focusProperties { left = closeFocus }
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
private fun BarButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
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
        modifier = modifier.size(BarButtonSize).semantics { contentDescription = description },
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
