package org.tubepipe.core.service

import java.io.IOException
import java.time.OffsetDateTime
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.tubepipe.core.service.kiosk.YoutubeTrendingGamingVideosExtractor
import org.tubepipe.core.service.kiosk.YoutubeTrendingGamingVideosLinkHandlerFactory
import org.tubepipe.core.service.kiosk.YoutubeTrendingMoviesAndShowsTrailersExtractor
import org.tubepipe.core.service.kiosk.YoutubeTrendingMoviesAndShowsTrailersLinkHandlerFactory
import org.tubepipe.core.service.kiosk.YoutubeTrendingMusicExtractor
import org.tubepipe.core.service.kiosk.YoutubeTrendingMusicLinkHandlerFactory

/**
 * The only door from the app to YouTube content. Every extractor call is blocking network I/O,
 * so each public function moves it to [ioDispatcher]. Call [ExtractorInitializer.initialize] first.
 */
class YoutubeContentRepository(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : ContentRepository {
    private val youtube = ServiceList.YouTube

    override suspend fun search(query: String): ContentPage<VideoItem> = extracting("search", query) {
        val searchInfo = SearchInfo.getInfo(youtube, videoSearchHandlerFor(query))
        videoPageOf(searchInfo.relatedItems, searchInfo.nextPage)
    }

    override suspend fun searchMore(query: String, cursor: PageCursor): ContentPage<VideoItem> =
        extracting("searchMore", query) {
            val page = SearchInfo.getMoreItems(youtube, videoSearchHandlerFor(query), cursor.page)
            videoPageOf(page.items, page.nextPage)
        }

    override suspend fun searchSuggestions(query: String): List<String> = extracting("searchSuggestions", query) {
        youtube.suggestionExtractor.suggestionList(query)
    }

    override suspend fun trending(kind: TrendingKind): List<VideoItem> = extracting("trending", kind) {
        val streamItems = if (kind == TrendingKind.News) {
            YoutubeNewsFeed.fetch()
        } else {
            val extractor = trendingExtractorFor(kind)
            extractor.fetchPage()
            extractor.initialPage.items
        }
        streamItems.map { it.toVideoItem() }
    }

    override suspend fun videoDetails(url: String): VideoDetails = extracting("videoDetails", url) {
        val streamInfo = StreamInfo.getInfo(youtube, url)
        VideoDetails(
            video = streamInfo.toVideoItem(),
            description = streamInfo.description?.content.orEmpty(),
            likeCount = streamInfo.likeCount.nullIfNegative(),
            channelAvatarUrl = streamInfo.uploaderAvatarUrl,
            related = streamInfo.relatedItems.toVideoItems(),
        )
    }

    override suspend fun mix(videoUrl: String): List<VideoItem> = extracting("mix", videoUrl) {
        // A YouTube Mix is the playlist "RD" + video id, opened on the seed video.
        val videoId = youtube.streamLHFactory.getId(videoUrl)
        val mixUrl = "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"
        PlaylistInfo.getInfo(youtube, mixUrl).relatedItems.toVideoItems()
    }

    override suspend fun channel(url: String): ChannelPage = extracting("channel", url) {
        val channelInfo = ChannelInfo.getInfo(youtube, url)
        ChannelPage(
            channel = ChannelSummary(
                url = channelInfo.url,
                name = channelInfo.name,
                avatarUrl = channelInfo.avatarUrl,
                subscriberCount = channelInfo.subscriberCount.nullIfNegative(),
            ),
            videos = videoPageOf(channelInfo.relatedItems, channelInfo.nextPage),
        )
    }

    override suspend fun channelVideosMore(url: String, cursor: PageCursor): ContentPage<VideoItem> =
        extracting("channelVideosMore", url) {
            val page = ChannelInfo.getMoreItems(youtube, url, cursor.page)
            videoPageOf(page.items, page.nextPage)
        }

    /** Newest first across all channels. A channel that fails is logged and left out of the feed. */
    override suspend fun latestFromChannels(channelUrls: List<String>): List<VideoItem> = coroutineScope {
        val concurrencyLimit = Semaphore(MAX_CONCURRENT_CHANNEL_FETCHES)
        val itemsPerChannel = channelUrls.map { channelUrl ->
            async {
                concurrencyLimit.withPermit { latestStreamItemsOrEmpty(channelUrl) }
            }
        }.awaitAll()

        // Items without a parsed upload date sort last instead of being dropped.
        itemsPerChannel
            .flatten()
            .sortedByDescending { item -> item.uploadDate?.offsetDateTime() ?: OffsetDateTime.MIN }
            .map { it.toVideoItem() }
    }

    private suspend fun latestStreamItemsOrEmpty(channelUrl: String): List<StreamInfoItem> = withContext(ioDispatcher) {
        try {
            ChannelInfo.getInfo(youtube, channelUrl).relatedItems.filterIsInstance<StreamInfoItem>()
        } catch (failure: IOException) {
            logSkippedChannel(channelUrl, failure)
            emptyList()
        } catch (failure: ExtractionException) {
            logSkippedChannel(channelUrl, failure)
            emptyList()
        }
    }

    private fun logSkippedChannel(channelUrl: String, failure: Exception) {
        logger.log(Level.WARNING, "Skipping channel $channelUrl in the subscription feed", failure)
    }

    // YouTube's query handler throws unless a content filter is chosen, so pick the "videos" one.
    private fun videoSearchHandlerFor(query: String): SearchQueryHandler {
        val videosFilter = youtube.searchQHFactory.availableContentFilter.filterGroups
            .flatMap { group -> group.filterItems.asList() }
            .first { filterItem -> filterItem.name == YoutubeFilters.VIDEOS }
        return youtube.searchQHFactory.fromQuery(query, listOf(videosFilter), null)
    }

    private fun trendingExtractorFor(kind: TrendingKind): KioskExtractor<StreamInfoItem> = when (kind) {
        TrendingKind.Music -> {
            val kioskId = YoutubeTrendingMusicLinkHandlerFactory.KIOSK_ID
            val linkHandler = YoutubeTrendingMusicLinkHandlerFactory.INSTANCE.fromId(kioskId)
            YoutubeTrendingMusicExtractor(youtube, linkHandler, kioskId)
        }
        TrendingKind.MoviesAndShows -> {
            val kioskId = YoutubeTrendingMoviesAndShowsTrailersLinkHandlerFactory.KIOSK_ID
            val linkHandler = YoutubeTrendingMoviesAndShowsTrailersLinkHandlerFactory.INSTANCE.fromId(kioskId)
            YoutubeTrendingMoviesAndShowsTrailersExtractor(youtube, linkHandler, kioskId)
        }
        TrendingKind.Gaming -> {
            val kioskId = YoutubeTrendingGamingVideosLinkHandlerFactory.KIOSK_ID
            val linkHandler = YoutubeTrendingGamingVideosLinkHandlerFactory.INSTANCE.fromId(kioskId)
            YoutubeTrendingGamingVideosExtractor(youtube, linkHandler, kioskId)
        }
        // News is not a kiosk: trending() reads it through YoutubeNewsFeed and never gets here.
        TrendingKind.News -> throw IllegalArgumentException("News has no kiosk extractor")
    }

    private fun videoPageOf(items: List<InfoItem>, nextPage: Page?): ContentPage<VideoItem> = ContentPage(
        items = items.toVideoItems(),
        next = nextPage?.let { PageCursor(it) },
    )

    // Search and related lists also hold channels and playlists; the app shows videos only.
    private fun List<InfoItem>.toVideoItems(): List<VideoItem> =
        filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }

    private companion object {
        const val MAX_CONCURRENT_CHANNEL_FETCHES = 4
        val logger: Logger = Logger.getLogger(YoutubeContentRepository::class.java.name)
    }

    /**
     * Runs one extractor call on [ioDispatcher]. ExtractionException is invisible outside this
     * module, so it is rethrown as IOException naming the call and its subject.
     */
    private suspend fun <T> extracting(operation: String, subject: Any, block: () -> T): T = withContext(ioDispatcher) {
        try {
            block()
        } catch (error: ExtractionException) {
            throw IOException("YouTube $operation failed for $subject: ${error.message}", error)
        }
    }
}
