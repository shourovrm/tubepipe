package org.tubepipe.core.player

/** The three facts about a video stream that decide whether it is chosen. */
data class VideoCandidate(val height: Int, val codec: String, val bitrate: Int)

object VideoStreamChoice {
    /**
     * Picks the tallest stream at or below [maxHeight], preferring codecs that start with [preferredCodecPrefix]
     * (the Mi Box S decodes avc1 in hardware). When every stream is taller than [maxHeight], the smallest one wins,
     * because a stream that is too large still plays while no stream does not.
     */
    fun pick(candidates: List<VideoCandidate>, maxHeight: Int, preferredCodecPrefix: String): VideoCandidate? {
        val withinLimit = candidates.filter { candidate -> candidate.height <= maxHeight }
        if (withinLimit.isEmpty()) {
            return candidates.minWithOrNull(compareBy({ it.height }, { it.bitrate }))
        }
        val inPreferredCodec = withinLimit.filter { candidate -> candidate.codec.startsWith(preferredCodecPrefix) }
        val pool = inPreferredCodec.ifEmpty { withinLimit }
        return pool.maxWithOrNull(compareBy({ it.height }, { it.bitrate }))
    }
}
