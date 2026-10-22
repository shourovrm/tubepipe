package org.tubepipe.core.ui.component

import java.util.Locale

object TimeFormatting {
    /** "3:32" under an hour, "1:02:03" from an hour up, as YouTube shows durations. */
    fun duration(totalSeconds: Long): String {
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Share of the video already watched, for the red progress bar on a thumbnail. */
    fun watchedFraction(positionMillis: Long, durationSeconds: Long?): Float {
        if (durationSeconds == null || durationSeconds <= 0) {
            return 0f
        }
        val fraction = positionMillis / (durationSeconds * 1_000f)
        return fraction.coerceIn(0f, 1f)
    }
}
