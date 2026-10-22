package org.tubepipe.tv.testing

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.tubepipe.core.service.ChannelPage
import org.tubepipe.core.service.ChannelSummary
import org.tubepipe.core.service.ContentPage
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.PageCursor
import org.tubepipe.core.service.PlaylistPage
import org.tubepipe.core.service.PlaylistSummary
import org.tubepipe.core.service.SearchResult
import org.tubepipe.core.service.TrendingKind
import org.tubepipe.core.service.VideoDetails
import org.tubepipe.core.service.VideoItem

fun video(id: String, channel: String = "Channel", durationSeconds: Long? = 600L): VideoItem = VideoItem(
    url = "https://www.youtube.com/watch?v=$id",
    title = "Video $id",
    channelName = channel,
    channelUrl = "https://www.youtube.com/channel/$channel",
    thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
    durationSeconds = durationSeconds,
    viewCount = 1_000L,
    uploadedText = "1 day ago",
    isLive = false,
)

fun videos(prefix: String, count: Int): List<VideoItem> = (1..count).map { video("$prefix$it") }

/**
 * In-memory stand-in for YouTube. Each lookup fails with IOException when its key is in
 * [failures]: "search", "suggestions", "trending:<Kind>", "details:<url>", "mix:<url>",
 * "channel:<url>", "playlist:<url>", "feed".
 */
class FakeContentRepository : ContentRepository {
    val searchFirstPages = mutableMapOf<String, List<VideoItem>>()

    /** Channels and playlists the fake appends after the videos of the same query's first page. */
    val searchExtraResults = mutableMapOf<String, List<SearchResult>>()
    val searchSecondPages = mutableMapOf<String, List<VideoItem>>()
    val suggestions = mutableMapOf<String, List<String>>()
    val trendingVideos = mutableMapOf<TrendingKind, List<VideoItem>>()
    val details = mutableMapOf<String, VideoDetails>()
    val mixes = mutableMapOf<String, List<VideoItem>>()
    val channels = mutableMapOf<String, ChannelPage>()
    val channelSecondPages = mutableMapOf<String, List<VideoItem>>()
    val playlists = mutableMapOf<String, PlaylistPage>()
    val playlistSecondPages = mutableMapOf<String, List<VideoItem>>()
    var feed: List<VideoItem> = emptyList()

    /** When set, videoDetails waits for it, so a test can observe what shows before the network answers. */
    var detailsGate: CompletableDeferred<Unit>? = null
    val failures = mutableSetOf<String>()

    val suggestionRequests = mutableListOf<String>()
    val trendingRequests = mutableListOf<TrendingKind>()
    var feedRequest: List<String>? = null
    private val searchCursors = mutableMapOf<String, PageCursor>()
    private val channelCursors = mutableMapOf<String, PageCursor>()
    private val playlistCursors = mutableMapOf<String, PageCursor>()

    private fun failIfRequested(key: String) {
        if (key in failures) {
            throw IOException("fake failure for $key")
        }
    }

    override suspend fun search(query: String): ContentPage<SearchResult> {
        failIfRequested("search")
        val cursor = if (query in searchSecondPages) PageCursor.forTesting(query) else null
        if (cursor != null) {
            searchCursors[query] = cursor
        }
        val firstPage = searchFirstPages[query].orEmpty().map { SearchResult.Video(it) } +
            searchExtraResults[query].orEmpty()
        return ContentPage(firstPage, cursor)
    }

    override suspend fun searchMore(query: String, cursor: PageCursor): ContentPage<SearchResult> {
        failIfRequested("search")
        check(searchCursors[query] === cursor) { "searchMore called with a cursor the fake did not hand out" }
        return ContentPage(searchSecondPages[query].orEmpty().map { SearchResult.Video(it) }, null)
    }

    override suspend fun searchSuggestions(query: String): List<String> {
        failIfRequested("suggestions")
        suggestionRequests += query
        return suggestions[query].orEmpty()
    }

    override suspend fun trending(kind: TrendingKind): List<VideoItem> {
        failIfRequested("trending:$kind")
        trendingRequests += kind
        return trendingVideos[kind].orEmpty()
    }

    override suspend fun videoDetails(url: String): VideoDetails {
        detailsGate?.await()
        failIfRequested("details:$url")
        return details[url] ?: throw IOException("no fake details for $url")
    }

    override suspend fun mix(videoUrl: String): List<VideoItem> {
        failIfRequested("mix:$videoUrl")
        return mixes[videoUrl].orEmpty()
    }

    override suspend fun channel(url: String): ChannelPage {
        failIfRequested("channel:$url")
        val page = channels[url] ?: throw IOException("no fake channel for $url")
        val cursor = if (url in channelSecondPages) PageCursor.forTesting(url) else null
        if (cursor != null) {
            channelCursors[url] = cursor
        }
        return page.copy(videos = page.videos.copy(next = cursor))
    }

    override suspend fun channelVideosMore(url: String, cursor: PageCursor): ContentPage<VideoItem> {
        failIfRequested("channel:$url")
        check(channelCursors[url] === cursor) { "channelVideosMore called with a cursor the fake did not hand out" }
        return ContentPage(channelSecondPages[url].orEmpty(), null)
    }

    override suspend fun playlist(url: String): PlaylistPage {
        failIfRequested("playlist:$url")
        val page = playlists[url] ?: throw IOException("no fake playlist for $url")
        val cursor = if (url in playlistSecondPages) PageCursor.forTesting(url) else null
        if (cursor != null) {
            playlistCursors[url] = cursor
        }
        return page.copy(videos = page.videos.copy(next = cursor))
    }

    override suspend fun playlistMore(url: String, cursor: PageCursor): ContentPage<VideoItem> {
        failIfRequested("playlist:$url")
        check(playlistCursors[url] === cursor) { "playlistMore called with a cursor the fake did not hand out" }
        return ContentPage(playlistSecondPages[url].orEmpty(), null)
    }

    override suspend fun latestFromChannels(channelUrls: List<String>): List<VideoItem> {
        failIfRequested("feed")
        feedRequest = channelUrls
        return feed
    }

    fun addDetails(video: VideoItem, related: List<VideoItem>) {
        details[video.url] = VideoDetails(
            video = video,
            description = "Description of ${video.title}",
            likeCount = 10L,
            channelAvatarUrl = null,
            related = related,
        )
    }

    fun addPlaylist(url: String, title: String, firstPage: List<VideoItem>, uploaderName: String? = "Uploader") {
        playlists[url] = PlaylistPage(
            playlist = PlaylistSummary(
                url = url,
                title = title,
                thumbnailUrl = null,
                uploaderName = uploaderName,
                videoCount = firstPage.size.toLong(),
            ),
            videos = ContentPage(firstPage, null),
        )
    }

    fun addChannel(url: String, name: String, firstPage: List<VideoItem>) {
        channels[url] = ChannelPage(
            channel = ChannelSummary(url = url, name = name, avatarUrl = null, subscriberCount = 5_000L),
            videos = ContentPage(firstPage, null),
        )
    }
}
