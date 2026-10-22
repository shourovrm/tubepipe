package org.tubepipe.tv.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.tubepipe.core.ui.theme.TubepipeColors

private val TrackHeight = 6.dp
private val ThumbRadius = 9.dp
private val TrackColor = Color(0x66FFFFFF)

/** SponsorBlock category colours from docs/design-system.md; unlisted categories are drawn white. */
internal fun segmentColor(category: String): Color = when (category) {
    "sponsor" -> Color(0xFF00D400)
    "intro" -> Color(0xFF00FFFF)
    "outro" -> Color(0xFF0202ED)
    "interaction" -> Color(0xFFCC00FF)
    else -> Color.White
}

/** The red seek bar: grey track, SponsorBlock segments, red elapsed part and a red thumb. */
@Composable
fun PlayerSeekBar(
    positionMillis: Long,
    durationMillis: Long,
    segments: List<SkipSegment>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(ThumbRadius * 2)) {
        val trackHeightPx = TrackHeight.toPx()
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
        drawCircle(TubepipeColors.Primary, ThumbRadius.toPx(), Offset(elapsedWidth, size.height / 2))
    }
}
