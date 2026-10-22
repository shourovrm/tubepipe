package org.tubepipe.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMenusTest {
    private fun videoFormat(height: Int): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setWidth(height * 16 / 9)
        .setHeight(height)
        .build()

    private fun videoGroup(heights: List<Int>, support: List<Int> = heights.map { C.FORMAT_HANDLED }): Tracks.Group =
        Tracks.Group(
            TrackGroup(*heights.map(::videoFormat).toTypedArray()),
            true,
            support.toIntArray(),
            BooleanArray(heights.size),
        )

    private fun textGroup(language: String, label: String?): Tracks.Group = Tracks.Group(
        TrackGroup(
            Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage(language).setLabel(label).build(),
        ),
        false,
        intArrayOf(C.FORMAT_HANDLED),
        booleanArrayOf(false),
    )

    @Test
    fun qualitiesAreDistinctHeightsHighestFirst() {
        val tracks = Tracks(listOf(videoGroup(listOf(1080, 720, 360)), videoGroup(listOf(720, 480))))

        assertEquals(listOf(1080, 720, 480, 360), TrackMenus.qualities(tracks))
    }

    @Test
    fun tracksTheDeviceCannotPlayAreLeftOut() {
        val tracks = Tracks(
            listOf(
                videoGroup(
                    heights = listOf(2160, 1080),
                    support = listOf(C.FORMAT_EXCEEDS_CAPABILITIES, C.FORMAT_HANDLED),
                ),
            ),
        )

        assertEquals(listOf(1080), TrackMenus.qualities(tracks))
    }

    @Test
    fun captionsListEachTextTrackWithItsLabel() {
        val tracks = Tracks(listOf(textGroup("en", "English"), textGroup("bn", null)))

        assertEquals(
            listOf(CaptionOption(language = "en", label = "English"), CaptionOption(language = "bn", label = "bn")),
            TrackMenus.captions(tracks),
        )
    }

    @Test
    fun noTracksMeansEmptyMenus() {
        assertEquals(emptyList<Int>(), TrackMenus.qualities(Tracks.EMPTY))
        assertEquals(emptyList<CaptionOption>(), TrackMenus.captions(Tracks.EMPTY))
    }
}
