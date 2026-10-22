package org.tubepipe.core.player

import androidx.media3.common.C
import androidx.media3.common.Tracks

/** Builds the quality and captions menus from the tracks the player currently exposes. */
object TrackMenus {
    /** Distinct heights of the video tracks this device can play, tallest first. */
    fun qualities(tracks: Tracks): List<Int> {
        val heights = mutableSetOf<Int>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) {
                continue
            }
            for (trackIndex in 0 until group.length) {
                val height = group.getTrackFormat(trackIndex).height
                if (group.isTrackSupported(trackIndex) && height > 0) {
                    heights.add(height)
                }
            }
        }
        return heights.sortedDescending()
    }

    /** Every text track, labelled by its label or else its language code. */
    fun captions(tracks: Tracks): List<CaptionOption> {
        val options = mutableListOf<CaptionOption>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) {
                continue
            }
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val language = format.language ?: continue
                options.add(CaptionOption(language, format.label ?: language))
            }
        }
        return options
    }
}
