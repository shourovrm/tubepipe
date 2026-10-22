package org.tubepipe.tv.player

import java.net.URI
import java.net.URISyntaxException

/** Reads the 11-character video id out of the URL shapes YouTube and the extractor produce. */
object YoutubeVideoId {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val idPathPrefixes = setOf("shorts", "embed", "live")

    /** Returns null for URLs that do not point at one video, such as channel pages. */
    fun from(url: String): String? {
        val uri = try {
            URI(url)
        } catch (malformed: URISyntaxException) {
            // A string that is not a URL cannot name a video; callers treat that the same as a channel URL.
            return null
        }
        val host = uri.host?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val pathSegments = uri.path.orEmpty().split("/").filter { it.isNotEmpty() }
        val candidate = when (host) {
            "youtu.be" -> pathSegments.firstOrNull()
            "youtube.com", "music.youtube.com" -> idFromYoutubeUrl(uri, pathSegments)
            else -> null
        }
        return candidate?.takeIf { idPattern.matches(it) }
    }

    private fun idFromYoutubeUrl(uri: URI, pathSegments: List<String>): String? {
        if (pathSegments.firstOrNull() == "watch") {
            return queryParameter(uri, "v")
        }
        if (pathSegments.size >= 2 && pathSegments.first() in idPathPrefixes) {
            return pathSegments[1]
        }
        return null
    }

    private fun queryParameter(uri: URI, name: String): String? {
        val query = uri.rawQuery ?: return null
        return query.split("&")
            .map { pair -> pair.substringBefore("=") to pair.substringAfter("=", missingDelimiterValue = "") }
            .firstOrNull { (key, _) -> key == name }
            ?.second
    }
}
