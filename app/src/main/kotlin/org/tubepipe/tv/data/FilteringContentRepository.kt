package org.tubepipe.tv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.tubepipe.core.service.ChannelPage
import org.tubepipe.core.service.ContentPage
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.PageCursor
import org.tubepipe.core.service.PlaylistPage
import org.tubepipe.core.service.SearchResult
import org.tubepipe.core.service.TrendingKind
import org.tubepipe.core.service.VideoDetails
import org.tubepipe.core.service.VideoItem

/**
 * Applies the Settings content filters (Shorts, upcoming, members-only) to every list of videos the
 * delegate returns. The switches are read on each call, so a change applies to the next load.
 */
class FilteringContentRepository(private val delegate: ContentRepository, private val settings: Flow<AppSettings>) :
    ContentRepository {
    override suspend fun search(query: String): ContentPage<SearchResult> = delegate.search(query).withFilteredVideos()

    override suspend fun searchMore(query: String, cursor: PageCursor): ContentPage<SearchResult> =
        delegate.searchMore(query, cursor).withFilteredVideos()

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

    override suspend fun playlist(url: String): PlaylistPage {
        val page = delegate.playlist(url)
        return page.copy(videos = page.videos.filtered())
    }

    override suspend fun playlistMore(url: String, cursor: PageCursor): ContentPage<VideoItem> =
        delegate.playlistMore(url, cursor).filtered()

    override suspend fun latestFromChannels(channelUrls: List<String>): List<VideoItem> =
        delegate.latestFromChannels(channelUrls).filtered()

    // Channels and playlists have no Short or members-only flag, so only the video entries are tested.
    private suspend fun ContentPage<SearchResult>.withFilteredVideos(): ContentPage<SearchResult> {
        val current = settings.first()
        return copy(
            items = items.filterNot { result -> result is SearchResult.Video && current.hides(result.video) },
        )
    }

    private suspend fun ContentPage<VideoItem>.filtered(): ContentPage<VideoItem> = copy(items = items.filtered())

    private suspend fun List<VideoItem>.filtered(): List<VideoItem> {
        val current = settings.first()
        return filterNot { video -> current.hides(video) }
    }

    private fun AppSettings.hides(video: VideoItem): Boolean = (hideShorts && video.isShort) ||
        (hideUpcoming && video.isUpcoming) ||
        (hidePaid && video.isMembersOnly)
}
