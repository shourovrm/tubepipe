package org.tubepipe.core.service

/** YouTube content as plain models; screens depend on this so their tests can use a fake. */
interface ContentRepository {
    suspend fun search(query: String): ContentPage<VideoItem>

    suspend fun searchMore(query: String, cursor: PageCursor): ContentPage<VideoItem>

    suspend fun searchSuggestions(query: String): List<String>

    suspend fun trending(kind: TrendingKind): List<VideoItem>

    suspend fun videoDetails(url: String): VideoDetails

    /** The YouTube Mix playlist seeded by one video ("Mix for X" on Home). */
    suspend fun mix(videoUrl: String): List<VideoItem>

    suspend fun channel(url: String): ChannelPage

    suspend fun channelVideosMore(url: String, cursor: PageCursor): ContentPage<VideoItem>

    suspend fun latestFromChannels(channelUrls: List<String>): List<VideoItem>
}
