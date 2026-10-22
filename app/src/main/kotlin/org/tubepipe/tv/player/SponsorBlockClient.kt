package org.tubepipe.tv.player

import java.io.IOException
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.tubepipe.tv.data.SponsorBlockCategory

/** A stretch of the video that SponsorBlock says to skip, in milliseconds. */
data class SkipSegment(val startMillis: Long, val endMillis: Long, val category: String)

interface SponsorBlockSource {
    suspend fun segments(videoId: String, categories: Set<SponsorBlockCategory>): List<SkipSegment>
}

/** Reads skip segments from the public SponsorBlock API (https://wiki.sponsor.ajay.app/w/API_Docs). */
class SponsorBlockClient(private val httpClient: OkHttpClient = OkHttpClient()) : SponsorBlockSource {
    /**
     * @throws IOException when the server is unreachable, answers with an error, or sends malformed JSON
     */
    override suspend fun segments(videoId: String, categories: Set<SponsorBlockCategory>): List<SkipSegment> =
        withContext(Dispatchers.IO) {
            val url = API_URL.toHttpUrl().newBuilder()
                .addQueryParameter("videoID", videoId)
                .addQueryParameter("categories", JSONArray(categories.map { it.apiName }).toString())
                .build()
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                // SponsorBlock answers 404 for a video nobody has submitted segments for.
                if (response.code == HTTP_NOT_FOUND) {
                    return@withContext emptyList()
                }
                if (!response.isSuccessful) {
                    throw IOException("SponsorBlock answered HTTP ${response.code} for video $videoId")
                }
                val body = response.body.string()
                try {
                    parse(body)
                } catch (malformed: JSONException) {
                    throw IOException("SponsorBlock sent malformed JSON for video $videoId", malformed)
                }
            }
        }

    companion object {
        private const val API_URL = "https://sponsor.ajay.app/api/skipSegments"
        private const val HTTP_NOT_FOUND = 404
        private const val MILLIS_PER_SECOND = 1_000

        /** Keeps `skip` actions only (mute and full-video labels are not seekable), sorted by start. */
        fun parse(json: String): List<SkipSegment> {
            val entries = JSONArray(json)
            val segments = mutableListOf<SkipSegment>()
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                if (entry.optString("actionType") != "skip") {
                    continue
                }
                val bounds = entry.getJSONArray("segment")
                segments += SkipSegment(
                    startMillis = (bounds.getDouble(0) * MILLIS_PER_SECOND).roundToLong(),
                    endMillis = (bounds.getDouble(1) * MILLIS_PER_SECOND).roundToLong(),
                    category = entry.getString("category"),
                )
            }
            return segments.sortedBy { it.startMillis }
        }
    }
}
