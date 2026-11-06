package org.tubepipe.tv.data

import java.text.Normalizer
import java.util.Locale
import org.tubepipe.core.service.SearchResult
import org.tubepipe.core.service.VideoItem

private val CombiningMarks = Regex("\\p{Mn}+")
private val SeparatorsAndPunctuation = Regex("[\\p{P}\\p{S}\\p{Z}\\s]+")

/** True when the Parental settings hide [video] from every list. */
fun AppSettings.hides(video: VideoItem): Boolean = (hideShorts && video.isShort) ||
    (hideUpcoming && video.isUpcoming) ||
    (hidePaid && video.isMembersOnly) ||
    blockedWords.blockedWordIn(video) != null

/** True when a search result is hidden: a video by [hides], a channel or playlist by its name alone. */
fun AppSettings.hides(result: SearchResult): Boolean = when (result) {
    is SearchResult.Video -> hides(result.video)
    is SearchResult.Channel -> blockedWords.blockedWordIn(result.channel.name) != null
    is SearchResult.Playlist ->
        blockedWords.blockedWordIn(result.playlist.title, result.playlist.uploaderName.orEmpty()) != null
}

/** The first blocked word found in [video]'s title or channel name, or null when none matches. */
fun Set<String>.blockedWordIn(video: VideoItem): String? = blockedWordIn(video.title, video.channelName)

/** The first blocked word found in any of [texts], or null when none matches. */
fun Set<String>.blockedWordIn(vararg texts: String): String? {
    if (isEmpty()) {
        return null
    }
    val folded = texts.map(::foldForBlocking)
    return firstOrNull { word ->
        val needle = foldForBlocking(word)
        needle.isNotEmpty() && folded.any { it.contains(needle) }
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
