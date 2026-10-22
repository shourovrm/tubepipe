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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tubepipe.core.ui.component.TimeFormatting
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.navigation.AppNavigator

private val MiniVideoWidth = 320.dp
private val MiniVideoHeight = 180.dp
private val MiniRadius = 12.dp
private val IconButtonSize = 38.dp
private val IconSize = 22.dp
private val TooltipOffset = 44.dp
private val SwitchWidth = 30.dp
private val SwitchHeight = 17.dp
private val SwitchThumbSize = 12.dp
private val SwitchOffColor = Color(0xFF4A4A4A)
private const val HINT_VISIBLE_MILLIS = 3_000L

/**
 * Focus hand-over between the shell and the corner window. The shell's Back key moves focus onto the window
 * ([focusOnBack]); the window reports back whether it holds focus, and whether the hint chip is showing.
 * Lives in [AppContainer] because the Back key is handled outside the window's composition.
 */
class MiniPlayerFocus {
    internal val playPauseRequester = FocusRequester()

    var hasFocus by mutableStateOf(false)
        internal set

    var isHintVisible by mutableStateOf(false)
        internal set

    /** True while the window is composed, so a focus request cannot reach a button that does not exist. */
    internal var isWindowShown by mutableStateOf(false)

    /**
     * Previous and Next replace the player engine, which recomposes the window from scratch; the new window
     * takes the focus back so the user can press Next again.
     */
    internal var restoreFocusAfterSwitch = false

    /** Moves focus onto the window on the first Back; returns false when the Back should go on as normal. */
    fun focusOnBack(): Boolean {
        if (!isWindowShown || hasFocus) {
            return false
        }
        isHintVisible = true
        playPauseRequester.requestFocus()
        return true
    }
}

/**
 * Bottom-right window over whatever screen is shown. Centre on the picture returns to full screen; a row of
 * round icon buttons below it controls playback. The media Play/Pause key is handled by the activity.
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
    val miniFocus = container.miniPlayerFocus
    val viewModel = rememberPlayerViewModel(videoUrl, container)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLooping by controller.isLooping.collectAsStateWithLifecycle()
    val snapshot = engine.snapshot
    val videoDescription = stringResource(R.string.mini_video_description)
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        miniFocus.isWindowShown = true
        onDispose {
            miniFocus.isWindowShown = false
            miniFocus.hasFocus = false
            miniFocus.isHintVisible = false
        }
    }
    LaunchedEffect(Unit) {
        if (miniFocus.restoreFocusAfterSwitch) {
            miniFocus.restoreFocusAfterSwitch = false
            // Wait one frame so the button is laid out; requesting focus on an unplaced node throws.
            withFrameNanos { }
            miniFocus.playPauseRequester.requestFocus()
        }
    }
    LaunchedEffect(miniFocus.isHintVisible) {
        if (miniFocus.isHintVisible) {
            delay(HINT_VISIBLE_MILLIS)
            miniFocus.isHintVisible = false
        }
    }

    fun openFullScreen() {
        controller.expand()
        navigator.openVideo(videoUrl)
    }

    // Same as the end-of-video switch in PlaybackHost: nobody is watching full screen, so the video stays small.
    fun switchTo(otherVideoUrl: String) {
        miniFocus.restoreFocusAfterSwitch = miniFocus.hasFocus
        controller.engineFor(otherVideoUrl)
        controller.minimize()
    }

    fun playPrevious() {
        val previousVideoUrl = controller.previousVideoUrl()
        if (previousVideoUrl != null) {
            switchTo(previousVideoUrl)
        } else {
            engine.player?.exoPlayer?.seekTo(0L)
        }
    }

    Column(
        modifier = modifier.padding(
            end = TubepipeSpacing.SafeMarginHorizontal,
            bottom = TubepipeSpacing.SafeMarginVertical,
        ),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 2),
    ) {
        if (miniFocus.isHintVisible) {
            Text(
                text = stringResource(R.string.mini_back_hint),
                style = MaterialTheme.typography.titleMedium,
                color = TubepipeColors.OnSurface,
                modifier = Modifier
                    .background(TubepipeColors.SurfaceVariant, RoundedCornerShape(MiniRadius))
                    .padding(horizontal = TubepipeSpacing.Grid * 4, vertical = TubepipeSpacing.Grid * 2),
            )
        }
        Column(
            modifier = Modifier
                .width(MiniVideoWidth)
                .background(TubepipeColors.Surface, RoundedCornerShape(MiniRadius))
                .onFocusChanged { focusState ->
                    // Losing focus ends the hint early; the 3 s timer only covers a window nobody leaves.
                    if (!focusState.hasFocus) {
                        miniFocus.isHintVisible = false
                    }
                    miniFocus.hasFocus = focusState.hasFocus
                },
        ) {
            Surface(
                onClick = ::openFullScreen,
                shape = ClickableSurfaceDefaults.shape(
                    RoundedCornerShape(topStart = MiniRadius, topEnd = MiniRadius),
                ),
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
                PlayerSurface(
                    player = engine.player?.exoPlayer,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier.size(width = MiniVideoWidth, height = MiniVideoHeight),
                )
            }
            MiniProgress(snapshot)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TubepipeSpacing.Grid * 4, vertical = TubepipeSpacing.Grid * 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TubepipeSpacing.Grid * 2, end = TubepipeSpacing.Grid * 2, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MiniIconButton(label = stringResource(R.string.mini_button_previous), onClick = ::playPrevious) {
                    ButtonIcon(PlayerIcons.SkipPrevious)
                }
                val playPauseLabel = if (snapshot.playWhenReady) {
                    R.string.mini_button_pause
                } else {
                    R.string.mini_button_play
                }
                MiniIconButton(
                    label = stringResource(playPauseLabel),
                    onClick = controller::togglePause,
                    modifier = Modifier.focusRequester(miniFocus.playPauseRequester),
                ) {
                    ButtonIcon(if (snapshot.playWhenReady) PlayerIcons.Pause else Icons.Filled.PlayArrow)
                }
                MiniIconButton(
                    label = stringResource(R.string.mini_button_next),
                    onClick = { viewModel.nextVideoUrl()?.let(::switchTo) },
                ) {
                    ButtonIcon(PlayerIcons.SkipNext)
                }
                MiniIconButton(label = stringResource(R.string.mini_button_loop), onClick = controller::toggleLoop) {
                    ButtonIcon(PlayerIcons.Loop, tint = if (isLooping) TubepipeColors.FocusBorder else Color.White)
                }
                MiniIconButton(
                    label = stringResource(R.string.mini_button_autoplay),
                    onClick = { scope.launch { container.settings.setAutoplayNext(!state.autoplayNext) } },
                ) {
                    AutoplaySwitch(isOn = state.autoplayNext)
                }
                MiniIconButton(label = stringResource(R.string.mini_button_full_screen), onClick = ::openFullScreen) {
                    ButtonIcon(PlayerIcons.Fullscreen)
                }
                MiniIconButton(label = stringResource(R.string.mini_button_close), onClick = controller::stop) {
                    ButtonIcon(PlayerIcons.Close)
                }
            }
        }
    }
}

/** A round icon button; only the focused one shows [label], in a chip below it. */
@Composable
private fun MiniIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(contentAlignment = Alignment.TopCenter) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = TubepipeColors.SurfaceVariant,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder), shape = CircleShape),
            ),
            modifier = modifier
                .size(IconButtonSize)
                .onFocusChanged { focusState -> isFocused = focusState.isFocused },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(IconButtonSize)) {
                icon()
            }
        }
        if (isFocused) {
            // Unbounded so the chip can be wider than the 38 dp button without widening the row.
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TubepipeColors.OnSurface,
                maxLines = 1,
                modifier = Modifier
                    .wrapContentSize(unbounded = true, align = Alignment.TopCenter)
                    .offset(y = TooltipOffset)
                    .background(TubepipeColors.SurfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ButtonIcon(icon: ImageVector, tint: Color = Color.White) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSize))
}

/** The autoplay state as a small switch: red with the knob on the right when on. */
@Composable
private fun AutoplaySwitch(isOn: Boolean) {
    Box(
        modifier = Modifier
            .size(width = SwitchWidth, height = SwitchHeight)
            .clip(RoundedCornerShape(SwitchHeight / 2))
            .background(if (isOn) TubepipeColors.Primary else SwitchOffColor),
        contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(SwitchThumbSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** The thin red line under the picture; also drawn as the fallback thumbnail of the drawer's "Now playing". */
@Composable
internal fun MiniProgress(snapshot: PlaybackSnapshot, lineHeight: Dp = 4.dp, modifier: Modifier = Modifier) {
    val fraction = if (snapshot.durationMillis > 0) {
        (snapshot.positionMillis.toFloat() / snapshot.durationMillis).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(modifier = modifier.fillMaxWidth().height(lineHeight).background(TubepipeColors.SurfaceVariant)) {
        Box(modifier = Modifier.fillMaxWidth(fraction).height(lineHeight).background(TubepipeColors.Primary))
    }
}
