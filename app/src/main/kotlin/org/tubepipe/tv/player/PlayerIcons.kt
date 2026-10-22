package org.tubepipe.tv.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The material icon set bundled with the app has no transport icons, so these are the standard 24 dp
// Material Symbols paths, drawn here.
private fun transportIcon(name: String, pathData: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()

internal object PlayerIcons {
    val Pause = transportIcon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")

    val SkipPrevious = transportIcon("SkipPrevious", "M6,6h2v12H6zM9.5,12l8.5,6V6z")

    val SkipNext = transportIcon("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")

    /** Counter-clockwise arrow, for seeking back. */
    val SeekBack = transportIcon(
        "SeekBack",
        "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 " +
            "-3.58,-8 -8,-8z",
    )

    /** Clockwise arrow, for seeking forward. */
    val SeekForward = transportIcon(
        "SeekForward",
        "M12,5V1l5,5 -5,5V7c-3.31,0 -6,2.69 -6,6s2.69,6 6,6 6,-2.69 6,-6h2c0,4.42 -3.58,8 -8,8s-8,-3.58 -8,-8 " +
            "3.58,-8 8,-8z",
    )
}
