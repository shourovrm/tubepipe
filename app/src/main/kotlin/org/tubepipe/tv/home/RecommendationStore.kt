package org.tubepipe.tv.home

import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.tubepipe.core.service.VideoItem

/** Remembers the last Recommended row so Home can show it before the network answers. */
interface RecommendationStore {
    suspend fun load(): List<VideoItem>

    suspend fun save(videos: List<VideoItem>)
}

class FileRecommendationStore(private val file: File) : RecommendationStore {
    override suspend fun load(): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext emptyList()
        }
        // The file is only a cache, so a damaged one is logged and ignored rather than failing Home.
        try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { index -> array.getJSONObject(index).toVideoItem() }
        } catch (failure: IOException) {
            logUnreadable(failure)
        } catch (failure: JSONException) {
            logUnreadable(failure)
        }
    }

    override suspend fun save(videos: List<VideoItem>) {
        withContext(Dispatchers.IO) {
            val array = JSONArray()
            videos.forEach { video -> array.put(video.toJson()) }
            try {
                file.writeText(array.toString())
            } catch (failure: IOException) {
                logger.log(Level.WARNING, "Could not save recommendations to ${file.path}", failure)
            }
        }
    }

    private fun logUnreadable(failure: Exception): List<VideoItem> {
        logger.log(Level.WARNING, "Ignoring unreadable recommendations file ${file.path}", failure)
        return emptyList()
    }

    private fun VideoItem.toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("title", title)
        .put("channelName", channelName)
        .put("channelUrl", channelUrl ?: JSONObject.NULL)
        .put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)
        .put("durationSeconds", durationSeconds ?: JSONObject.NULL)
        .put("viewCount", viewCount ?: JSONObject.NULL)
        .put("uploadedText", uploadedText ?: JSONObject.NULL)
        .put("isLive", isLive)

    private fun JSONObject.toVideoItem(): VideoItem = VideoItem(
        url = getString("url"),
        title = getString("title"),
        channelName = getString("channelName"),
        channelUrl = optionalString("channelUrl"),
        thumbnailUrl = optionalString("thumbnailUrl"),
        durationSeconds = optionalLong("durationSeconds"),
        viewCount = optionalLong("viewCount"),
        uploadedText = optionalString("uploadedText"),
        isLive = getBoolean("isLive"),
    )

    // optString would turn a JSON null into the text "null".
    private fun JSONObject.optionalString(name: String): String? = if (isNull(name)) null else getString(name)

    private fun JSONObject.optionalLong(name: String): Long? = if (isNull(name)) null else getLong(name)

    private companion object {
        val logger: Logger = Logger.getLogger(FileRecommendationStore::class.java.name)
    }
}
