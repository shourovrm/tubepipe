package org.tubepipe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistDao {
    @Query("INSERT INTO playlist (name, createdAtMillis) VALUES (:name, :createdAtMillis)")
    abstract suspend fun createPlaylist(name: String, createdAtMillis: Long): Long

    @Query("SELECT * FROM playlist ORDER BY id ASC")
    abstract fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("DELETE FROM playlist WHERE id = :id")
    abstract suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_item WHERE playlistId = :playlistId ORDER BY position ASC")
    abstract fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_item WHERE playlistId = :playlistId AND url = :url)")
    abstract fun observeContains(playlistId: Long, url: String): Flow<Boolean>

    @Query("DELETE FROM playlist_item WHERE playlistId = :playlistId AND url = :url")
    abstract suspend fun removeItem(playlistId: Long, url: String)

    @Query("SELECT COALESCE(MAX(position), 0) FROM playlist_item WHERE playlistId = :playlistId")
    protected abstract suspend fun highestPosition(playlistId: Long): Long

    // IGNORE keeps the existing row, and with it its original position, when the video is added a second time.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertItem(item: PlaylistItemEntity)

    /** Creates the playlist and puts [video] in it as one step; returns the new playlist's id. */
    @Transaction
    open suspend fun createPlaylistWithVideo(name: String, video: PlaylistVideo, createdAtMillis: Long): Long {
        val playlistId = createPlaylist(name, createdAtMillis)
        addItem(playlistId, video)
        return playlistId
    }

    @Transaction
    open suspend fun addItem(playlistId: Long, video: PlaylistVideo) {
        insertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                url = video.url,
                title = video.title,
                channelName = video.channelName,
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds,
                position = highestPosition(playlistId) + 1,
            ),
        )
    }
}
