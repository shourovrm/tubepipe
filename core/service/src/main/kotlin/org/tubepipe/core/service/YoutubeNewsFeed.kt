package org.tubepipe.core.service

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import java.nio.charset.StandardCharsets
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeLockupStreamInfoItemExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector

// Adapted from fyi-player (MIT, same author): app/source/newpipe/YoutubeTopicFeed.kt
/**
 * The News tab is the feed of YouTube's official News topic channel, read with one desktop `browse`
 * request. The channel's first tab holds videos in a `richGridRenderer`, either as direct items or
 * inside `richShelfRenderer` shelves. There is no continuation, so this is a single page.
 */
internal object YoutubeNewsFeed {
    private const val NEWS_CHANNEL_BROWSE_ID = "UCYfdidRxbB8Qhf0Nx7ioOYw"
    private const val VIDEO_LOCKUP_CONTENT_TYPE = "LOCKUP_CONTENT_TYPE_VIDEO"

    private val FALLBACK_COUNTRY = ContentCountry("US")

    /** Blocking network call; throws IOException or ExtractionException on failure. */
    fun fetch(): List<StreamInfoItem> = withUsFallback(NewPipe.getPreferredContentCountry(), ::fetchFor)

    /**
     * Where YouTube does not offer the News channel it answers 200 with no videos (CN, KP, IR, AF, NP, MM on
     * 2026-10-23), so an empty feed is read again for the US. Failures are not retried: they would fail for the US too.
     */
    internal fun <T> withUsFallback(preferredCountry: ContentCountry, fetchFor: (ContentCountry) -> List<T>): List<T> {
        val items = fetchFor(preferredCountry)
        if (items.isNotEmpty() || preferredCountry == FALLBACK_COUNTRY) {
            return items
        }
        return fetchFor(FALLBACK_COUNTRY)
    }

    private fun fetchFor(contentCountry: ContentCountry): List<StreamInfoItem> {
        val localization = NewPipe.getPreferredLocalization()
        val requestBody = JsonWriter.string(
            YoutubeParsingHelper.prepareDesktopJsonBuilder(localization, contentCountry)
                .value("browseId", NEWS_CHANNEL_BROWSE_ID)
                .done(),
        ).toByteArray(StandardCharsets.UTF_8)
        val response = YoutubeParsingHelper.getJsonPostResponse("browse", requestBody, localization)

        val timeAgoParser = ServiceList.YouTube.getTimeAgoParser(localization)
        val collector = StreamInfoItemsCollector(ServiceList.YouTube.serviceId)
        for (content in itemContents(response)) {
            if (content.has("videoRenderer")) {
                collector.commit(YoutubeStreamInfoItemExtractor(content.getObject("videoRenderer"), timeAgoParser))
            } else if (content.getObject("lockupViewModel").getString("contentType") == VIDEO_LOCKUP_CONTENT_TYPE) {
                collector.commit(
                    YoutubeLockupStreamInfoItemExtractor(content.getObject("lockupViewModel"), timeAgoParser),
                )
            }
        }
        // The same video can appear in several shelves.
        return collector.items.distinctBy { item -> item.url }
    }

    /** The `content` of every richItemRenderer in the first tab; Shorts shelves are not matched, so they are skipped. */
    private fun itemContents(response: JsonObject): List<JsonObject> {
        val firstTab = response.getObject("contents").getObject("twoColumnBrowseResultsRenderer")
            .getArray("tabs").filterIsInstance<JsonObject>().firstOrNull() ?: return emptyList()
        val gridEntries = firstTab.getObject("tabRenderer").getObject("content")
            .getObject("richGridRenderer").getArray("contents").filterIsInstance<JsonObject>()

        val contents = mutableListOf<JsonObject>()
        for (entry in gridEntries) {
            if (entry.has("richItemRenderer")) {
                contents += entry.getObject("richItemRenderer").getObject("content")
                continue
            }
            val shelf = entry.getObject("richSectionRenderer").getObject("content").getObject("richShelfRenderer")
            for (shelfEntry in shelf.getArray("contents").filterIsInstance<JsonObject>()) {
                if (shelfEntry.has("richItemRenderer")) {
                    contents += shelfEntry.getObject("richItemRenderer").getObject("content")
                }
            }
        }
        return contents
    }
}
