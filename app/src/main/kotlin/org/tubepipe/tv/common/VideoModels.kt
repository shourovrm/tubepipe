package org.tubepipe.tv.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import java.util.Locale
import org.tubepipe.core.database.HistoryEntity
import org.tubepipe.core.database.PlaylistItemEntity
import org.tubepipe.core.database.PlaylistVideo
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.VideoCardModel
import org.tubepipe.tv.R

/** "1.2M", "34K", "999": the compact counts YouTube shows. */
object CountFormatting {
    fun compact(count: Long): String = when {
        count >= 1_000_000_000 -> scaled(count, 1_000_000_000.0, "B")
        count >= 1_000_000 -> scaled(count, 1_000_000.0, "M")
        count >= 1_000 -> scaled(count, 1_000.0, "K")
        else -> count.toString()
    }

    private fun scaled(count: Long, divisor: Double, suffix: String): String {
        val value = count / divisor
        val text = if (value < 10) String.format(Locale.ROOT, "%.1f", value) else value.toLong().toString()
        return text.removeSuffix(".0") + suffix
    }
}

/** "Channel · 1.2M views · 2 days ago", leaving out the parts YouTube did not give. */
@Composable
fun videoSubtitle(video: VideoItem): String {
    val parts = mutableListOf(video.channelName)
    val views = video.viewCount
    if (views != null) {
        parts +=
            pluralStringResource(
                R.plurals.view_count,
                views.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                CountFormatting.compact(views),
            )
    }
    val uploaded = video.uploadedText
    if (uploaded != null) {
        parts += uploaded
    }
    return parts.joinToString(separator = " · ")
}

@Composable
fun VideoItem.toCardModel(watchedFraction: Float = 0f): VideoCardModel = VideoCardModel(
    title = title,
    subtitle = videoSubtitle(this),
    thumbnailUrl = thumbnailUrl,
    // Shorts in search results come with a duration of 0; no badge reads better than "0:00".
    durationSeconds = durationSeconds?.takeIf { it > 0 },
    isLive = isLive,
    watchedFraction = watchedFraction,
    videoUrl = url,
)

fun VideoItem.toPlaylistVideo(): PlaylistVideo = PlaylistVideo(
    url = url,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
)

/** Local rows store fewer fields than YouTube returns; unknown ones become null. */
fun HistoryEntity.toVideoItem(): VideoItem = VideoItem(
    url = url,
    title = title,
    channelName = channelName,
    channelUrl = null,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = null,
    uploadedText = null,
    isLive = false,
)

fun PlaylistItemEntity.toVideoItem(): VideoItem = VideoItem(
    url = url,
    title = title,
    channelName = channelName,
    channelUrl = null,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = null,
    uploadedText = null,
    isLive = false,
)

fun VideoItem.toHistoryEntity(watchedAtMillis: Long, positionMillis: Long): HistoryEntity = HistoryEntity(
    url = url,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    lastWatchedAtMillis = watchedAtMillis,
    positionMillis = positionMillis,
)
