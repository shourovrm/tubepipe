package org.tubepipe.tv.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.tubepipe.core.service.VideoDetails
import org.tubepipe.core.ui.component.TimeFormatting
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.R
import org.tubepipe.tv.channel.ChannelAvatar
import org.tubepipe.tv.common.CountFormatting
import org.tubepipe.tv.navigation.PlaybackQueue

private val SmallButtonSize = 48.dp
private val PlayButtonSize = 72.dp
private val PopupWidth = 260.dp
private val PopupRowHeight = 56.dp
private val PopupMaxHeight = 392.dp
private val PopupGap = 12.dp
private val LabelGap = 4.dp
private val ButtonBackground = Color(0x14FFFFFF)
private val ButtonBackgroundFocused = Color(0x38FFFFFF)
private val LoopOnBackground = Color(0x59E53935)
private val SwitchOffTrack = Color(0xFF6B6B6B)
private val SwitchTrackWidth = 36.dp
private val SwitchTrackHeight = 20.dp
private val SwitchKnobSize = 16.dp
private val SeparatorWidth = 2.dp
private val SeparatorHeight = 40.dp
private val ButtonRowGap = 8.dp
private val ScrimTop = Color(0x00000000)
private val ScrimBottom = Color(0xEB000000)

/** The buttons whose Centre press opens a small list above them. */
internal enum class ControlList { Captions, Speed, Quality, More }

internal class ListOption(val label: String, val isCurrent: Boolean, val onSelect: () -> Unit)

internal class ControlBarActions(
    val onPrevious: () -> Unit,
    val onSeekBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSeekForward: () -> Unit,
    val onNext: () -> Unit,
    val onOpenList: (ControlList) -> Unit,
    val onToggleLike: () -> Unit,
    val onToggleLoop: () -> Unit,
    val onToggleAutoplay: () -> Unit,
    val onOpenChannel: () -> Unit,
)

/**
 * The one control bar of mockup A: title, channel chip, seek bar with SponsorBlock segments, and one row of
 * round buttons. [openList] is the list shown above its button, with [listOptions] as its entries and
 * [listKey] changing whenever the entries are a different list (so focus returns to the current entry).
 */
@Composable
internal fun PlayerControlBar(
    details: VideoDetails,
    snapshot: PlaybackSnapshot,
    engine: TubepipePlayerEngine,
    state: PlayerState,
    queue: PlaybackQueue?,
    isLooping: Boolean,
    openList: ControlList?,
    listOptions: List<ListOption>,
    listKey: Any,
    playPauseFocus: FocusRequester,
    onButtonRowFocusChanged: (Boolean) -> Unit,
    actions: ControlBarActions,
    modifier: Modifier = Modifier,
) {
    val openerFocus = remember { ControlList.entries.associateWith { FocusRequester() } }
    val seekBarFocus = remember { FocusRequester() }
    val channelChipFocus = remember { FocusRequester() }
    var lastOpened by remember { mutableStateOf<ControlList?>(null) }
    LaunchedEffect(openList) {
        if (openList != null) {
            lastOpened = openList
        } else {
            lastOpened?.let { opener -> openerFocus.getValue(opener).requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ScrimTop, ScrimBottom)))
            .padding(
                start = TubepipeSpacing.SafeMarginHorizontal,
                end = TubepipeSpacing.SafeMarginHorizontal,
                top = PlayButtonSize,
                bottom = TubepipeSpacing.SafeMarginVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
    ) {
        Text(
            text = details.video.title,
            style = MaterialTheme.typography.headlineMedium,
            color = TubepipeColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        ChannelLine(
            details = details,
            queue = queue,
            onOpenChannel = actions.onOpenChannel,
            chipFocus = channelChipFocus,
            downTarget = seekBarFocus,
        )
        SeekRow(
            snapshot = snapshot,
            details = details,
            segments = state.segments,
            focus = SeekBarFocus(
                requester = seekBarFocus,
                onSeekBack = actions.onSeekBack,
                onSeekForward = actions.onSeekForward,
                upTarget = channelChipFocus,
                downTarget = playPauseFocus,
            ),
        )
        ButtonRow(
            snapshot = snapshot,
            engine = engine,
            state = state,
            isLooping = isLooping,
            openList = openList,
            listOptions = listOptions,
            listKey = listKey,
            playPauseFocus = playPauseFocus,
            seekBarFocus = seekBarFocus,
            openerFocus = openerFocus,
            onButtonRowFocusChanged = onButtonRowFocusChanged,
            actions = actions,
        )
    }
}

@Composable
private fun ChannelLine(
    details: VideoDetails,
    queue: PlaybackQueue?,
    onOpenChannel: () -> Unit,
    chipFocus: FocusRequester,
    downTarget: FocusRequester,
) {
    val video = details.video
    val viewsAndAge = listOfNotNull(
        video.viewCount?.let { views ->
            pluralStringResource(
                R.plurals.view_count,
                views.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                CountFormatting.compact(views),
            )
        },
        video.uploadedText,
    ).joinToString(separator = " · ")
    val queuePosition = queue?.let {
        stringResource(R.string.player_queue_position, it.index + 1, it.videoUrls.size, it.title)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            onClick = onOpenChannel,
            enabled = video.channelUrl != null,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TubepipeColors.SurfaceVariant,
                focusedContainerColor = TubepipeColors.SurfaceVariant,
                contentColor = TubepipeColors.OnSurface,
                focusedContentColor = TubepipeColors.OnSurface,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = focusBorder(),
            modifier = Modifier
                .focusRequester(chipFocus)
                .focusProperties { down = downTarget },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 6.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            ) {
                ChannelAvatar(avatarUrl = details.channelAvatarUrl, size = 40.dp)
                Text(text = video.channelName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
        Text(
            text = viewsAndAge,
            style = MaterialTheme.typography.titleMedium,
            color = TubepipeColors.OnSurfaceVariant,
        )
        if (queuePosition != null) {
            Text(
                text = queuePosition,
                style = MaterialTheme.typography.titleMedium,
                color = TubepipeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeekRow(
    snapshot: PlaybackSnapshot,
    details: VideoDetails,
    segments: List<SkipSegment>,
    focus: SeekBarFocus? = null,
) {
    val durationMillis = durationOf(snapshot, details)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = TimeFormatting.duration(snapshot.positionMillis / 1_000),
            style = MaterialTheme.typography.titleMedium,
            color = TubepipeColors.OnSurface,
        )
        PlayerSeekBar(
            positionMillis = snapshot.positionMillis,
            durationMillis = durationMillis,
            segments = segments,
            modifier = Modifier.weight(1f),
            focus = focus,
        )
        Text(
            text = TimeFormatting.duration(durationMillis / 1_000),
            style = MaterialTheme.typography.titleMedium,
            color = TubepipeColors.OnSurface,
        )
    }
}

private fun durationOf(snapshot: PlaybackSnapshot, details: VideoDetails): Long = if (snapshot.durationMillis > 0) {
    snapshot.durationMillis
} else {
    (details.video.durationSeconds ?: 0L) * 1_000L
}

/** Just the times and the seek bar, shown for a moment after a seek while the full bar is hidden. */
@Composable
internal fun SeekFeedback(
    details: VideoDetails,
    snapshot: PlaybackSnapshot,
    segments: List<SkipSegment>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ScrimTop, ScrimBottom)))
            .padding(
                start = TubepipeSpacing.SafeMarginHorizontal,
                end = TubepipeSpacing.SafeMarginHorizontal,
                top = PlayButtonSize,
                bottom = TubepipeSpacing.SafeMarginVertical,
            ),
    ) {
        SeekRow(snapshot, details, segments)
    }
}

@Composable
private fun ButtonRow(
    snapshot: PlaybackSnapshot,
    engine: TubepipePlayerEngine,
    state: PlayerState,
    isLooping: Boolean,
    openList: ControlList?,
    listOptions: List<ListOption>,
    listKey: Any,
    playPauseFocus: FocusRequester,
    seekBarFocus: FocusRequester,
    openerFocus: Map<ControlList, FocusRequester>,
    onButtonRowFocusChanged: (Boolean) -> Unit,
    actions: ControlBarActions,
) {
    val stepSeconds = (state.seekStepMillis / 1_000).toInt()
    val qualityLabel = engine.currentQuality?.let { height -> stringResource(R.string.player_quality_height, height) }
        ?: stringResource(R.string.player_quality_auto)

    @Composable
    fun BoxScope.popupFor(list: ControlList, alignment: Alignment.Horizontal) {
        if (openList == list) {
            OptionPopup(listOptions, listKey, alignment)
        }
    }

    Row(
        verticalAlignment = Alignment.Top,
        // Thirteen buttons must fit the 844 dp between the safe margins of a 960 dp screen.
        horizontalArrangement = Arrangement.spacedBy(ButtonRowGap),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onButtonRowFocusChanged(it.hasFocus) }
            // On the Mi Box the default search never picked the seek bar above the buttons, so Up is explicit.
            // An open option list sits inside this row and needs Up for itself.
            .onPreviewKeyEvent { event ->
                val isUpPress = event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown
                if (isUpPress && openList == null) {
                    seekBarFocus.requestFocus()
                    true
                } else {
                    false
                }
            },
    ) {
        RoundControl(stringResource(R.string.player_previous), SmallButtonSize, actions.onPrevious) {
            ButtonIcon(PlayerIcons.SkipPrevious)
        }
        RoundControl(stringResource(R.string.player_seek_back, stepSeconds), SmallButtonSize, actions.onSeekBack) {
            ButtonIcon(PlayerIcons.SeekBack)
        }
        RoundControl(
            label = stringResource(if (snapshot.playWhenReady) R.string.player_pause else R.string.player_play),
            size = PlayButtonSize,
            onClick = actions.onPlayPause,
            modifier = Modifier.focusRequester(playPauseFocus),
        ) {
            ButtonIcon(if (snapshot.playWhenReady) PlayerIcons.Pause else Icons.Filled.PlayArrow, PlayButtonSize / 2)
        }
        RoundControl(
            stringResource(R.string.player_seek_forward, stepSeconds),
            SmallButtonSize,
            actions.onSeekForward,
        ) {
            ButtonIcon(PlayerIcons.SeekForward)
        }
        RoundControl(stringResource(R.string.player_next), SmallButtonSize, actions.onNext) {
            ButtonIcon(PlayerIcons.SkipNext)
        }
        Box(
            modifier = Modifier
                .padding(top = (PlayButtonSize - SeparatorHeight) / 2)
                .width(SeparatorWidth)
                .height(SeparatorHeight)
                .background(TubepipeColors.SurfaceVariant),
        )
        RoundControl(
            label = stringResource(if (isLooping) R.string.player_loop_on else R.string.player_loop_off),
            size = SmallButtonSize,
            onClick = actions.onToggleLoop,
            containerColor = if (isLooping) LoopOnBackground else ButtonBackground,
        ) {
            LoopIcon(isLooping)
        }
        RoundControl(
            label = stringResource(
                if (state.autoplayNext) R.string.player_autoplay_on else R.string.player_autoplay_off,
            ),
            size = SmallButtonSize,
            onClick = actions.onToggleAutoplay,
        ) {
            AutoplaySwitch(state.autoplayNext)
        }
        Box(modifier = Modifier.weight(1f))
        RoundControl(
            label = stringResource(R.string.player_captions),
            size = SmallButtonSize,
            onClick = { actions.onOpenList(ControlList.Captions) },
            modifier = Modifier.focusRequester(openerFocus.getValue(ControlList.Captions)),
            popup = { popupFor(ControlList.Captions, Alignment.CenterHorizontally) },
        ) {
            ButtonText("CC")
        }
        RoundControl(
            label = stringResource(R.string.player_speed),
            size = SmallButtonSize,
            onClick = { actions.onOpenList(ControlList.Speed) },
            modifier = Modifier.focusRequester(openerFocus.getValue(ControlList.Speed)),
            popup = { popupFor(ControlList.Speed, Alignment.CenterHorizontally) },
        ) {
            ButtonText(stringResource(R.string.player_speed_value, speedText(engine.currentSpeed)))
        }
        RoundControl(
            label = qualityLabel,
            size = SmallButtonSize,
            onClick = { actions.onOpenList(ControlList.Quality) },
            modifier = Modifier.focusRequester(openerFocus.getValue(ControlList.Quality)),
            popup = { popupFor(ControlList.Quality, Alignment.CenterHorizontally) },
        ) {
            ButtonText("HD")
        }
        RoundControl(
            label = stringResource(R.string.player_like),
            size = SmallButtonSize,
            onClick = actions.onToggleLike,
        ) {
            ButtonIcon(if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder)
        }
        RoundControl(
            label = stringResource(R.string.player_more),
            size = SmallButtonSize,
            onClick = { actions.onOpenList(ControlList.More) },
            modifier = Modifier.focusRequester(openerFocus.getValue(ControlList.More)),
            popup = { popupFor(ControlList.More, Alignment.End) },
        ) {
            ButtonIcon(Icons.Filled.MoreVert)
        }
    }
}

internal fun speedText(speed: Float): String = speed.toString().removeSuffix(".0")

@Composable
private fun ButtonIcon(icon: ImageVector, size: Dp = SmallButtonSize / 2) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(size))
}

/** The loop glyph, red with a small dot under it while looping, grey otherwise. */
@Composable
private fun LoopIcon(isLooping: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = PlayerIcons.Loop,
            contentDescription = null,
            tint = if (isLooping) TubepipeColors.Primary else TubepipeColors.OnSurfaceVariant,
            modifier = Modifier.size(SmallButtonSize / 2),
        )
        if (isLooping) {
            Box(
                modifier = Modifier
                    .offset(y = SmallButtonSize / 2 - 2.dp)
                    .size(5.dp)
                    .background(TubepipeColors.Primary, CircleShape),
            )
        }
    }
}

/** A small switch: pill track (red when on, grey when off) with a white knob that carries a play glyph. */
@Composable
private fun AutoplaySwitch(isOn: Boolean) {
    Box(
        contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .size(SwitchTrackWidth, SwitchTrackHeight)
            .background(if (isOn) TubepipeColors.Primary else SwitchOffTrack, CircleShape)
            .padding(2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(SwitchKnobSize).background(Color.White, CircleShape),
        ) {
            Icon(
                imageVector = PlayerIcons.PlayArrow,
                contentDescription = null,
                tint = if (isOn) TubepipeColors.Primary else SwitchOffTrack,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun ButtonText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun focusBorder() = ClickableSurfaceDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder), shape = CircleShape),
)

/** A round button; its [label] shows as a small chip below the button only while the button has focus. */
@Composable
private fun RoundControl(
    label: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = ButtonBackground,
    popup: @Composable BoxScope.() -> Unit = {},
    icon: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.height(PlayButtonSize).width(size),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = containerColor,
                focusedContainerColor = ButtonBackgroundFocused,
                contentColor = TubepipeColors.OnSurface,
                focusedContentColor = TubepipeColors.OnSurface,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = focusBorder(),
            modifier = modifier.size(size).onFocusChanged { isFocused = it.hasFocus },
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() }
        }
        popup()
        if (isFocused) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TubepipeColors.OnSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .floatBelow(LabelGap)
                    .background(TubepipeColors.SurfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Places the label's top edge just below the bottom of its parent, centred, without taking room in the parent. */
private fun Modifier.floatBelow(gap: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = Constraints.Infinity,
            maxHeight = Constraints.Infinity,
        ),
    )
    layout(placeable.width, 0) { placeable.place(0, gap.roundToPx()) }
}

/** Places the popup's bottom edge just above the top of its parent without taking any room in the parent. */
private fun Modifier.floatAbove(gap: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = Constraints.Infinity,
            maxHeight = Constraints.Infinity,
        ),
    )
    layout(placeable.width, 0) { placeable.place(0, -placeable.height - gap.roundToPx()) }
}

@Composable
private fun BoxScope.OptionPopup(options: List<ListOption>, listKey: Any, horizontal: Alignment.Horizontal) {
    // Keyed on the size too: Quality shows only Auto until the stream loads, then grows while the list is open.
    val focusRequesters = remember(listKey, options.size) { List(options.size) { FocusRequester() } }
    LaunchedEffect(listKey) {
        if (focusRequesters.isNotEmpty()) {
            val currentIndex = options.indexOfFirst { it.isCurrent }.coerceAtLeast(0)
            focusRequesters[currentIndex].requestFocus()
        }
    }
    Column(
        modifier = Modifier
            .align(if (horizontal == Alignment.End) Alignment.TopEnd else Alignment.TopCenter)
            .floatAbove(PopupGap)
            .width(PopupWidth)
            .heightIn(max = PopupMaxHeight)
            .background(TubepipeColors.Surface, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(TubepipeSpacing.Grid * 2),
    ) {
        options.forEachIndexed { index, option ->
            // Focus is trapped in the open list: the first Up and the last Down are swallowed.
            val keepsFocusInside = Modifier.onPreviewKeyEvent { event ->
                val isDownOnLast = event.key == Key.DirectionDown && index == options.lastIndex
                val isUpOnFirst = event.key == Key.DirectionUp && index == 0
                isDownOnLast || isUpOnFirst
            }
            OptionRow(option, Modifier.focusRequester(focusRequesters[index]).then(keepsFocusInside))
        }
    }
}

@Composable
private fun OptionRow(option: ListOption, modifier: Modifier) {
    Surface(
        onClick = option.onSelect,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = TubepipeColors.SurfaceVariant,
            contentColor = TubepipeColors.OnSurface,
            focusedContentColor = TubepipeColors.OnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder), shape = RoundedCornerShape(8.dp)),
        ),
        modifier = modifier.fillMaxWidth().height(PopupRowHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (option.isCurrent) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = TubepipeColors.Primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
