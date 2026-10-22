package org.tubepipe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMillis: Long,
) {
    companion object {
        /** Id of the built-in Liked playlist, created with the database. Its stored name is not shown to the user. */
        const val LIKED_ID = 1L
        const val LIKED_NAME = "liked"
    }
}
