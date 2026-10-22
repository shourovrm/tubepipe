package org.tubepipe.core.database

import org.json.JSONException
import org.json.JSONObject

data class ImportedSubscription(val url: String, val name: String)

/** Reads the subscriptions JSON that NewPipe and PipePipe export. */
object NewPipeSubscriptionsImport {
    private const val YOUTUBE_SERVICE_ID = 0

    /** Returns the YouTube subscriptions; entries of other services (SoundCloud, ...) are skipped. */
    fun parse(json: String): List<ImportedSubscription> {
        val root = try {
            JSONObject(json)
        } catch (error: JSONException) {
            throw IllegalArgumentException("Subscriptions export is not valid JSON: ${error.message}", error)
        }
        val entries = root.optJSONArray("subscriptions")
            ?: throw IllegalArgumentException(
                "Not a NewPipe or PipePipe subscriptions export: missing \"subscriptions\"",
            )

        val imported = mutableListOf<ImportedSubscription>()
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            if (entry.optInt("service_id", -1) != YOUTUBE_SERVICE_ID) continue
            imported += ImportedSubscription(url = entry.getString("url"), name = entry.getString("name"))
        }
        return imported
    }
}
