package org.tubepipe.tv.data

import java.text.Normalizer
import java.util.Locale
import org.tubepipe.core.service.VideoItem

private val CombiningMarks = Regex("\\p{Mn}+")
private val SeparatorsAndPunctuation = Regex("[\\p{P}\\p{S}\\p{Z}\\s]+")

/** True when the Parental settings hide [video] from every list. */
fun AppSettings.hides(video: VideoItem): Boolean = (hideShorts && video.isShort) ||
    (hideUpcoming && video.isUpcoming) ||
    (hidePaid && video.isMembersOnly) ||
    blockedWords.blockedWordIn(video) != null

/** The first blocked word found in [video]'s title or channel name, or null when none matches. */
fun Set<String>.blockedWordIn(video: VideoItem): String? {
    if (isEmpty()) {
        return null
    }
    val title = foldForBlocking(video.title)
    val channel = foldForBlocking(video.channelName)
    return firstOrNull { word ->
        val needle = foldForBlocking(word)
        needle.isNotEmpty() && (title.contains(needle) || channel.contains(needle))
    }
}

/**
 * Lower case, no accents, no spaces or punctuation, so the one entry "motu patlu" also catches "MOTU-PATLU" and
 * "MotuPatlu". Another script is other letters, not another spelling, and needs its own entry.
 */
internal fun foldForBlocking(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val withoutMarks = CombiningMarks.replace(decomposed, "")
    return SeparatorsAndPunctuation.replace(withoutMarks, "").lowercase(Locale.ROOT)
}
