package org.tubepipe.core.service

import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

internal fun StreamInfoItem.toVideoItem(): VideoItem = VideoItem(
    url = url,
    title = name,
    channelName = uploaderName.orEmpty(),
    channelUrl = uploaderUrl,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = duration.nullIfNegative(),
    viewCount = viewCount.nullIfNegative(),
    uploadedText = textualUploadDate,
    isLive = streamType.isLive(),
    isShort = isShortFormContent,
    isUpcoming = uploadDate.isInTheFuture(),
    isMembersOnly = requiresMembership(),
)

internal fun StreamInfo.toVideoItem(): VideoItem = VideoItem(
    url = url,
    title = name,
    channelName = uploaderName.orEmpty(),
    channelUrl = uploaderUrl,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = duration.nullIfNegative(),
    viewCount = viewCount.nullIfNegative(),
    uploadedText = UploadDateText.from(textualUploadDate),
    isLive = streamType.isLive(),
)

internal fun ChannelInfoItem.toChannelSummary(): ChannelSummary = ChannelSummary(
    url = url,
    name = name,
    avatarUrl = thumbnailUrl,
    subscriberCount = subscriberCount.nullIfNegative(),
)

internal fun PlaylistInfoItem.toPlaylistSummary(): PlaylistSummary = PlaylistSummary(
    url = url,
    title = name,
    thumbnailUrl = thumbnailUrl,
    uploaderName = uploaderName,
    videoCount = streamCount.nullIfNegative(),
)

/** Null for item kinds the app has no card for (for example a mix-type shelf entry). */
internal fun InfoItem.toSearchResultOrNull(): SearchResult? = when (this) {
    is StreamInfoItem -> SearchResult.Video(toVideoItem())
    is ChannelInfoItem -> SearchResult.Channel(toChannelSummary())
    is PlaylistInfoItem -> SearchResult.Playlist(toPlaylistSummary())
    else -> null
}

// The extractor reports "unknown" as -1 (and a few other negative markers).
internal fun Long.nullIfNegative(): Long? = if (this >= 0) this else null

// Premieres and scheduled streams carry their start time as the upload date.
private fun DateWrapper?.isInTheFuture(): Boolean = this != null && offsetDateTime().isAfter(OffsetDateTime.now())

private fun StreamType.isLive(): Boolean = this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM
