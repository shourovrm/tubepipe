package org.tubepipe.tv.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import org.tubepipe.core.ui.theme.TubepipeColors

private val TrackHeight = 6.dp
private val ThumbRadius = 9.dp
private val FocusedTrackHeight = 10.dp
private val FocusedThumbRadius = 14.dp
private val ThumbRingWidth = 3.dp
private val TrackColor = Color(0x66FFFFFF)

/** SponsorBlock category colours from docs/design-system.md; unlisted categories are drawn white. */
internal fun segmentColor(category: String): Color = when (category) {
    "sponsor" -> Color(0xFF00D400)
    "intro" -> Color(0xFF00FFFF)
    "outro" -> Color(0xFF0202ED)
    "interaction" -> Color(0xFFCC00FF)
    else -> Color.White
}

/** Left and Right seeks, Down hands focus on; Up is left to the focus search, which finds the channel chip. */
internal class SeekBarFocus(
    val requester: FocusRequester,
    val onSeekBack: () -> Unit,
    val onSeekForward: () -> Unit,
    val downTarget: FocusRequester,
)

/**
 * The red seek bar: grey track, SponsorBlock segments, red elapsed part and a red thumb. With [focus] set the
 * bar can be focused and then draws a thicker track and a larger thumb with a white ring.
 */
@Composable
internal fun PlayerSeekBar(
    positionMillis: Long,
    durationMillis: Long,
    segments: List<SkipSegment>,
    modifier: Modifier = Modifier,
    focus: SeekBarFocus? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusModifier = if (focus == null) {
        Modifier
    } else {
        Modifier
            .focusRequester(focus.requester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> focus.onSeekBack()
                    Key.DirectionRight -> focus.onSeekForward()
                    Key.DirectionDown -> focus.downTarget.requestFocus()
                    else -> return@onKeyEvent false
                }
                true
            }
    }
    Canvas(modifier = modifier.then(focusModifier).fillMaxWidth().height(FocusedThumbRadius * 2)) {
        val trackHeightPx = (if (isFocused) FocusedTrackHeight else TrackHeight).toPx()
        val thumbRadiusPx = (if (isFocused) FocusedThumbRadius else ThumbRadius).toPx()
        val trackTop = (size.height - trackHeightPx) / 2
        val corner = CornerRadius(trackHeightPx / 2)
        drawRoundRect(TrackColor, Offset(0f, trackTop), Size(size.width, trackHeightPx), corner)
        if (durationMillis <= 0) {
            return@Canvas
        }
        val elapsedFraction = (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        val elapsedWidth = size.width * elapsedFraction
        drawRoundRect(TubepipeColors.Primary, Offset(0f, trackTop), Size(elapsedWidth, trackHeightPx), corner)
        for (segment in segments) {
            val startX = size.width * (segment.startMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
            val endX = size.width * (segment.endMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
            drawRect(segmentColor(segment.category), Offset(startX, trackTop), Size(endX - startX, trackHeightPx))
        }
        val thumbCentre = Offset(elapsedWidth, size.height / 2)
        if (isFocused) {
            drawCircle(Color.White, thumbRadiusPx + ThumbRingWidth.toPx(), thumbCentre)
        }
        drawCircle(TubepipeColors.Primary, thumbRadiusPx, thumbCentre)
    }
}
