package org.tubepipe.tv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.tubepipe.core.service.ChannelPage
import org.tubepipe.core.service.ContentPage
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.PageCursor
import org.tubepipe.core.service.TrendingKind
import org.tubepipe.core.service.VideoDetails
import org.tubepipe.core.service.VideoItem

/**
 * Applies the Settings content filters (Shorts, upcoming, members-only) to every list of videos the
 * delegate returns. The switches are read on each call, so a change applies to the next load.
 */
class FilteringContentRepository(private val delegate: ContentRepository, private val settings: Flow<AppSettings>) :
    ContentRepository {
    override suspend fun search(query: String): ContentPage<VideoItem> = delegate.search(query).filtered()

    override suspend fun searchMore(query: String, cursor: PageCursor): ContentPage<VideoItem> =
        delegate.searchMore(query, cursor).filtered()

    // Suggestions are text, not videos.
    override suspend fun searchSuggestions(query: String): List<String> = delegate.searchSuggestions(query)

    override suspend fun trending(kind: TrendingKind): List<VideoItem> = delegate.trending(kind).filtered()

    // Only the related list is filtered: the opened video must play even if it is a Short.
    override suspend fun videoDetails(url: String): VideoDetails {
        val details = delegate.videoDetails(url)
        return details.copy(related = details.related.filtered())
    }

    override suspend fun mix(videoUrl: String): List<VideoItem> = delegate.mix(videoUrl).filtered()

    override suspend fun channel(url: String): ChannelPage {
        val page = delegate.channel(url)
        return page.copy(videos = page.videos.filtered())
    }

    override suspend fun channelVideosMore(url: String, cursor: PageCursor): ContentPage<VideoItem> =
        delegate.channelVideosMore(url, cursor).filtered()

    override suspend fun latestFromChannels(channelUrls: List<String>): List<VideoItem> =
        delegate.latestFromChannels(channelUrls).filtered()

    private suspend fun ContentPage<VideoItem>.filtered(): ContentPage<VideoItem> = copy(items = items.filtered())

    private suspend fun List<VideoItem>.filtered(): List<VideoItem> {
        val current = settings.first()
        return filterNot { video ->
            (current.hideShorts && video.isShort) ||
                (current.hideUpcoming && video.isUpcoming) ||
                (current.hidePaid && video.isMembersOnly)
        }
    }
}
