package org.tubepipe.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.CreationException
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.tubepipe.core.player.sabr.SabrDashMediaSource
import org.tubepipe.core.player.sabr.SabrSessionHelper
import org.tubepipe.core.player.youtube.YoutubeHttpDataSource

/** A media source together with the delivery method it was built from. */
class BuiltMediaSource(val mediaSource: MediaSource, val delivery: Delivery)

/**
 * Turns an extracted [StreamInfo] into a Media3 source, the way PipePipe's PlaybackResolver does:
 * HLS when the extractor offers it, else SABR, else separate DASH/progressive video and audio.
 * Building may do network I/O (SABR session, DASH manifest), so call [build] off the main thread.
 */
@OptIn(UnstableApi::class)
class StreamMediaSourceFactory(
    private val context: Context,
    private val maxVideoHeight: Int = MAX_VIDEO_HEIGHT,
    private val preferredCodecPrefix: String = PREFERRED_CODEC_PREFIX,
) {
    @Throws(IOException::class)
    fun build(streamInfo: StreamInfo, startPositionMs: Long): BuiltMediaSource {
        val hlsVariants = streamInfo.videoStreams.filter { stream -> stream.deliveryMethod == DeliveryMethod.HLS }
        val sabrVideos = streamInfo.videoOnlyStreams.filter { stream -> isSabrStream(stream) }
        val adaptiveVideos = (streamInfo.videoOnlyStreams + streamInfo.videoStreams).filter { stream ->
            isDashOrProgressive(stream)
        }
        // SABR comes first because the extractor offers it only for mweb, and it is the sole way to get mweb's
        // 1080p; the VisionOS client has no SABR streams and carries a master playlist instead.
        return when {
            sabrVideos.isNotEmpty() -> buildSabr(streamInfo, sabrVideos, startPositionMs)
            hlsVariants.isNotEmpty() -> buildHlsVariant(hlsVariants)
            // A master playlist: the track selector switches between its variants up to the height limit.
            streamInfo.hlsUrl.isNotEmpty() -> buildHls(streamInfo.hlsUrl)
            adaptiveVideos.isNotEmpty() -> buildAdaptive(streamInfo, adaptiveVideos)
            else -> throw IOException("No playable video stream in ${streamInfo.url}")
        }
    }

    /** One variant playlist the extractor already split out (live and ended live streams). */
    private fun buildHlsVariant(hlsVariants: List<VideoStream>): BuiltMediaSource {
        val variant = pickVideoStream(restrictToPreferredAudioTrack(hlsVariants))
            ?: throw IOException("No HLS variant could be chosen")
        return buildHls(variant.content)
    }

    private fun buildHls(playlistUrl: String): BuiltMediaSource {
        // Same extractor flags as PipePipe: detect access units so TS streams without an index still seek.
        val extractorFactory = DefaultHlsExtractorFactory(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS, true)
        val mediaSource = HlsMediaSource.Factory(youtubeDataSourceFactory(rangeParameter = false, rnParameter = false))
            .setExtractorFactory(extractorFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(playlistUrl)))
        return BuiltMediaSource(mediaSource, Delivery.Hls)
    }

    private fun buildSabr(
        streamInfo: StreamInfo,
        sabrVideos: List<VideoStream>,
        startPositionMs: Long,
    ): BuiltMediaSource {
        val video = pickVideoStream(sabrVideos) ?: throw IOException("No SABR video stream could be chosen")
        val sabrInfo = video.deliveryMethodInfo as YoutubeSabrInfo
        val sourceSpec = try {
            SabrSessionHelper.createSourceSpec(context, streamInfo.id, video.itag, streamInfo.audioStreams, sabrInfo)
        } catch (error: ExtractionException) {
            throw IOException("Could not describe the SABR source for ${streamInfo.id}", error)
        }
        val mediaItem = MediaItem.Builder().setUri(Uri.parse("sabr://${streamInfo.id}")).build()
        val mediaSource = SabrDashMediaSource(context, mediaItem, sourceSpec, startPositionMs)
        return BuiltMediaSource(mediaSource, Delivery.Sabr)
    }

    private fun buildAdaptive(streamInfo: StreamInfo, videos: List<VideoStream>): BuiltMediaSource {
        val video = pickVideoStream(videos) ?: throw IOException("No video stream could be chosen")
        val videoSource = buildAdaptiveSource(video, streamInfo)
        val delivery = if (video.deliveryMethod == DeliveryMethod.DASH) Delivery.Dash else Delivery.Progressive
        if (!video.isVideoOnly) {
            return BuiltMediaSource(videoSource, delivery)
        }
        val audioStreams = streamInfo.audioStreams.filter { stream -> isDashOrProgressive(stream) }
        val audioIndex = AudioTrackChoice.pickIndexOfStreams(audioStreams)
        if (audioIndex < 0) {
            throw IOException("Video-only stream chosen but ${streamInfo.url} has no audio stream")
        }
        val audioSource = buildAdaptiveSource(audioStreams[audioIndex], streamInfo)
        return BuiltMediaSource(MergingMediaSource(true, videoSource, audioSource), delivery)
    }

    private fun buildAdaptiveSource(stream: Stream, streamInfo: StreamInfo): MediaSource {
        val isMuxedProgressive = stream is VideoStream && !stream.isVideoOnly
        if (stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && isMuxedProgressive) {
            val progressiveFactory = youtubeDataSourceFactory(rangeParameter = false, rnParameter = true)
            return ProgressiveMediaSource.Factory(progressiveFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(stream.content)))
        }
        val dataSourceFactory = youtubeDataSourceFactory(rangeParameter = true, rnParameter = true)
        if (stream.deliveryMethod == DeliveryMethod.DASH && stream.isUrl) {
            return DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(stream.content)))
        }
        val manifestText = if (stream.deliveryMethod == DeliveryMethod.DASH) {
            otfManifest(stream, streamInfo.duration)
        } else {
            progressiveManifest(stream, streamInfo.duration)
        }
        return dashSourceFromManifest(manifestText, stream, dataSourceFactory)
    }

    private fun progressiveManifest(stream: Stream, durationSeconds: Long): String {
        val itagItem = stream.itagItem ?: throw IOException("Progressive stream ${stream.id} has no itag data")
        return try {
            YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, itagItem, durationSeconds)
        } catch (error: CreationException) {
            throw IOException("Could not build a DASH manifest for progressive stream ${stream.id}", error)
        }
    }

    private fun otfManifest(stream: Stream, durationSeconds: Long): String {
        val itagItem = stream.itagItem ?: throw IOException("OTF stream ${stream.id} has no itag data")
        return try {
            YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(stream.content, itagItem, durationSeconds)
        } catch (error: CreationException) {
            throw IOException("Could not build a DASH manifest for OTF stream ${stream.id}", error)
        }
    }

    private fun dashSourceFromManifest(
        manifestText: String,
        stream: Stream,
        dataSourceFactory: YoutubeHttpDataSource.Factory,
    ): MediaSource {
        val manifestBaseUri = Uri.parse(stream.manifestUrl.orEmpty())
        val manifest = ByteArrayInputStream(manifestText.toByteArray(StandardCharsets.UTF_8)).use { input ->
            DashManifestParser().parse(manifestBaseUri, input)
        }
        val mediaItem = MediaItem.fromUri(Uri.parse(stream.content))
        return DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest, mediaItem)
    }

    private fun isSabrStream(stream: VideoStream): Boolean =
        stream.deliveryMethod == DeliveryMethod.SABR && stream.deliveryMethodInfo is YoutubeSabrInfo

    private fun isDashOrProgressive(stream: Stream): Boolean = stream.deliveryMethod == DeliveryMethod.DASH ||
        stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

    /** Variants repeat once per audio track, so the original-language track is chosen before the picture. */
    private fun restrictToPreferredAudioTrack(variants: List<VideoStream>): List<VideoStream> {
        val trackIds = variants.mapNotNull { variant -> variant.audioTrackId }.distinct()
        if (trackIds.isEmpty()) {
            return variants
        }
        val tracks = trackIds.map { trackId ->
            val variantOfTrack = variants.first { variant -> variant.audioTrackId == trackId }
            AudioCandidate(trackId, variantOfTrack.audioTrackName, bitrate = 0)
        }
        val chosenTrackId = trackIds[AudioTrackChoice.pickIndex(tracks)]
        return variants.filter { variant -> variant.audioTrackId == chosenTrackId }
    }

    private fun pickVideoStream(streams: List<VideoStream>): VideoStream? {
        val candidates = streams.map { stream ->
            VideoCandidate(heightOf(stream), stream.codec.orEmpty(), stream.bitrate)
        }
        val chosen = VideoStreamChoice.pick(candidates, maxVideoHeight, preferredCodecPrefix) ?: return null
        // Candidates are fresh instances, so reference equality finds the stream even when two look alike.
        return streams[candidates.indexOfFirst { candidate -> candidate === chosen }]
    }

    /** HLS variants carry only a resolution string such as "1080p60", not a numeric height. */
    private fun heightOf(stream: VideoStream): Int {
        if (stream.height > 0) {
            return stream.height
        }
        return stream.resolution.takeWhile { character -> character.isDigit() }.toIntOrNull() ?: 0
    }

    private fun youtubeDataSourceFactory(rangeParameter: Boolean, rnParameter: Boolean): YoutubeHttpDataSource.Factory =
        YoutubeHttpDataSource.Factory()
            .setRangeParameterEnabled(rangeParameter)
            .setRnParameterEnabled(rnParameter)

    companion object {
        const val MAX_VIDEO_WIDTH = 1920
        const val MAX_VIDEO_HEIGHT = 1080
        const val PREFERRED_CODEC_PREFIX = "avc1"
    }
}
