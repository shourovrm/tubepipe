package org.tubepipe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per watched video; watching again updates the row, so history has no duplicates. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val lastWatchedAtMillis: Long,
    val positionMillis: Long,
)
