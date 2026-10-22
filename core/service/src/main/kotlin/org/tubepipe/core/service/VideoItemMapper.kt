package org.tubepipe.core.service

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
)

internal fun StreamInfo.toVideoItem(): VideoItem = VideoItem(
    url = url,
    title = name,
    channelName = uploaderName.orEmpty(),
    channelUrl = uploaderUrl,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = duration.nullIfNegative(),
    viewCount = viewCount.nullIfNegative(),
    uploadedText = textualUploadDate,
    isLive = streamType.isLive(),
)

// The extractor reports "unknown" as -1 (and a few other negative markers).
internal fun Long.nullIfNegative(): Long? = if (this >= 0) this else null

private fun StreamType.isLive(): Boolean = this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM
