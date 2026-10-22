package org.tubepipe.core.database

import androidx.room.Entity
import androidx.room.ForeignKey

/** A video as the UI hands it to a playlist; the database adds the position. */
data class PlaylistVideo(
    val url: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
)

/**
 * A video inside a playlist. The composite key stops the same url being stored twice in one playlist, and the
 * cascading foreign key removes the items together with their playlist.
 */
@Entity(
    tableName = "playlist_item",
    primaryKeys = ["playlistId", "url"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val url: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val position: Long,
)
