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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private val SmallButtonSize = 56.dp
private val PlayButtonSize = 72.dp
private val PopupWidth = 260.dp
private val PopupRowHeight = 56.dp
private val PopupMaxHeight = 392.dp
private val PopupGap = 12.dp
private val ButtonBackground = Color(0x14FFFFFF)
private val ButtonBackgroundFocused = Color(0x38FFFFFF)
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
    openList: ControlList?,
    listOptions: List<ListOption>,
    listKey: Any,
    playPauseFocus: FocusRequester,
    onButtonRowFocusChanged: (Boolean) -> Unit,
    actions: ControlBarActions,
    modifier: Modifier = Modifier,
) {
    val openerFocus = remember { ControlList.entries.associateWith { FocusRequester() } }
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
        ChannelLine(details, onOpenChannel = actions.onOpenChannel)
        SeekRow(snapshot, details, state.segments)
        ButtonRow(
            snapshot = snapshot,
            engine = engine,
            state = state,
            openList = openList,
            listOptions = listOptions,
            listKey = listKey,
            playPauseFocus = playPauseFocus,
            openerFocus = openerFocus,
            onButtonRowFocusChanged = onButtonRowFocusChanged,
            actions = actions,
        )
    }
}

@Composable
private fun ChannelLine(details: VideoDetails, onOpenChannel: () -> Unit) {
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
    }
}

@Composable
private fun SeekRow(snapshot: PlaybackSnapshot, details: VideoDetails, segments: List<SkipSegment>) {
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
    openList: ControlList?,
    listOptions: List<ListOption>,
    listKey: Any,
    playPauseFocus: FocusRequester,
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
        horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
        modifier = Modifier.fillMaxWidth().onFocusChanged { onButtonRowFocusChanged(it.hasFocus) },
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

@Composable
private fun ButtonText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun focusBorder() = ClickableSurfaceDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder), shape = CircleShape),
)

/** A round button with its label below; [popup] is drawn over the top of the button area. */
@Composable
private fun RoundControl(
    label: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    popup: @Composable BoxScope.() -> Unit = {},
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.height(PlayButtonSize).width(size),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onClick,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = ButtonBackground,
                    focusedContainerColor = ButtonBackgroundFocused,
                    contentColor = TubepipeColors.OnSurface,
                    focusedContentColor = TubepipeColors.OnSurface,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                border = focusBorder(),
                modifier = modifier.size(size),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() }
            }
            popup()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TubepipeColors.OnSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = TubepipeSpacing.Grid * 2),
        )
    }
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
    val focusRequesters = remember(listKey) { List(options.size) { FocusRequester() } }
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
            OptionRow(option, Modifier.focusRequester(focusRequesters[index]))
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
