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

    /** Two arrows in a loop; the player tints it red while the video repeats. */
    val Loop = transportIcon("Loop", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z")

    val PlayArrow = transportIcon("PlayArrow", "M8,5v14l11,-7z")

    val Fullscreen = transportIcon(
        "Fullscreen",
        "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z",
    )
}
