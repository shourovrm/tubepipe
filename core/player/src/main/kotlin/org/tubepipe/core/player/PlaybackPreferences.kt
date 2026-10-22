package org.tubepipe.core.player

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/** [codecPrefix] is how the extractor starts the codec string of streams in this codec, e.g. "avc1.64001f". */
@OptIn(UnstableApi::class)
enum class PreferredCodec(val mimeType: String, val codecPrefix: String) {
    H264(MimeTypes.VIDEO_H264, "avc1"),
    Vp9(MimeTypes.VIDEO_VP9, "vp09"),
    Av1(MimeTypes.VIDEO_AV1, "av01"),
}

/**
 * The user's playback defaults. [maxVideoHeight] null means automatic, capped at 1080 because the TV outputs 1080p.
 * [preferredAudioLanguage] null means the original-language track; [captionLanguage] null means captions off.
 */
data class PlaybackPreferences(
    val maxVideoHeight: Int? = 1080,
    val preferredCodec: PreferredCodec = PreferredCodec.H264,
    val preferredAudioLanguage: String? = null,
    val captionLanguage: String? = null,
    val speed: Float = 1f,
)

/** One entry of the captions menu; [language] is what [TubepipePlayer.setCaptions] takes. */
data class CaptionOption(val language: String, val label: String)
