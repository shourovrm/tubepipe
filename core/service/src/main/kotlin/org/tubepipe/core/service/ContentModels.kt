package org.tubepipe.core.service

import androidx.annotation.VisibleForTesting
import org.schabi.newpipe.extractor.Page

/** A video as the UI needs it. Unknown counts and durations are null, never a negative sentinel. */
data class VideoItem(
    val url: String,
    val title: String,
    val channelName: String,
    val channelUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val viewCount: Long?,
    val uploadedText: String?,
    val isLive: Boolean,
    // Used by the Settings content filters; defaults keep locally stored videos visible.
    val isShort: Boolean = false,
    val isUpcoming: Boolean = false,
    val isMembersOnly: Boolean = false,
)

data class VideoDetails(
    val video: VideoItem,
    val description: String,
    val likeCount: Long?,
    val channelAvatarUrl: String?,
    val related: List<VideoItem>,
    /** The minimum viewer age YouTube sets for the video; 0 when it has none. */
    val ageLimit: Int = 0,
) {
    val isAdultOnly: Boolean get() = ageLimit >= ADULT_AGE

    private companion object {
        const val ADULT_AGE = 18
    }
}

data class ChannelSummary(val url: String, val name: String, val avatarUrl: String?, val subscriberCount: Long?)

data class PlaylistSummary(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    /** Null when YouTube does not say how many videos the playlist has. */
    val videoCount: Long?,
)

/** One row of search results; YouTube mixes the three kinds in one list. */
sealed interface SearchResult {
    data class Video(val video: VideoItem) : SearchResult

    data class Channel(val channel: ChannelSummary) : SearchResult

    data class Playlist(val playlist: PlaylistSummary) : SearchResult
}

/** The videos among [this], in order; filters and the player queue only deal with videos. */
fun List<SearchResult>.videoItems(): List<VideoItem> = filterIsInstance<SearchResult.Video>().map { it.video }

data class PlaylistPage(val playlist: PlaylistSummary, val videos: ContentPage<VideoItem>)

data class ChannelPage(val channel: ChannelSummary, val videos: ContentPage<VideoItem>)

data class ContentPage<T>(val items: List<T>, val next: PageCursor?)

/**
 * Opaque handle for "the page after this one". It wraps the extractor's Page so that no
 * org.schabi type appears in this module's public API; only this module can build or read it.
 */
class PageCursor internal constructor(internal val page: Page) {
    companion object {
        /** Lets fakes in other modules' tests hand out a "next page" without touching extractor types. */
        @VisibleForTesting
        fun forTesting(token: String): PageCursor = PageCursor(Page(token))
    }
}
