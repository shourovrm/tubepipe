package org.tubepipe.tv.playlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tubepipe.core.database.PlaylistDao
import org.tubepipe.core.database.PlaylistEntity
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.PageCursor
import org.tubepipe.core.service.VideoItem
import org.tubepipe.tv.common.toVideoItem
import org.tubepipe.tv.navigation.PlaybackQueue
import org.tubepipe.tv.navigation.PlaylistSource

data class PlaylistState(
    val title: String = "",
    /** The uploader of a YouTube playlist; null for a local playlist or when YouTube gives none. */
    val ownerName: String? = null,
    /** Videos YouTube says the playlist has, or the local count; null when unknown. */
    val videoCount: Long? = null,
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val canLoadMore: Boolean = false,
)

/**
 * Backs the playlist page for both sources. A YouTube playlist is fetched page by page; a local
 * Library playlist is observed from the database, so removing a video elsewhere updates the page.
 * [likedPlaylistTitle] is the display name of the built-in Liked playlist, whose stored name is not shown.
 */
class PlaylistViewModel(
    private val source: PlaylistSource,
    private val content: ContentRepository,
    private val playlistDao: PlaylistDao,
    private val likedPlaylistTitle: String,
    private val random: Random = Random.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlaylistState())
    val state: StateFlow<PlaylistState> = mutableState

    private var nextPageCursor: PageCursor? = null
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        load()
    }

    fun retry() {
        mutableState.update { PlaylistState() }
        load()
    }

    fun loadMore() {
        val remoteSource = source as? PlaylistSource.Remote ?: return
        val cursor = nextPageCursor ?: return
        if (loadMoreJob?.isActive == true || mutableState.value.isLoading) {
            return
        }
        loadMoreJob = viewModelScope.launch {
            try {
                val page = content.playlistMore(remoteSource.playlistUrl, cursor)
                nextPageCursor = page.next
                mutableState.update {
                    it.copy(videos = it.videos + page.items, canLoadMore = page.next != null)
                }
            } catch (failure: IOException) {
                // Keep the videos already shown and the cursor, so a later scroll can try again.
                Log.w(TAG, "Loading more of $source failed", failure)
            }
        }
    }

    /** The queue that starts at the video at [index], or null when the list is empty or [index] is outside it. */
    fun queueStartingAt(index: Int): PlaybackQueue? {
        val current = mutableState.value
        if (index !in current.videos.indices) {
            return null
        }
        return PlaybackQueue(source, current.title, current.videos.map { it.url }, index)
    }

    /** A queue of the same videos in random order, starting at its first entry; null when the list is empty. */
    fun shuffledQueue(): PlaybackQueue? {
        val current = mutableState.value
        if (current.videos.isEmpty()) {
            return null
        }
        val shuffledUrls = current.videos.map { it.url }.shuffled(random)
        return PlaybackQueue(source, current.title, shuffledUrls, index = 0)
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = when (source) {
            is PlaylistSource.Remote -> viewModelScope.launch { loadRemote(source.playlistUrl) }
            is PlaylistSource.Local -> viewModelScope.launch { observeLocal(source.playlistId) }
        }
    }

    private suspend fun loadRemote(playlistUrl: String) {
        try {
            val page = content.playlist(playlistUrl)
            nextPageCursor = page.videos.next
            mutableState.value = PlaylistState(
                title = page.playlist.title,
                ownerName = page.playlist.uploaderName,
                videoCount = page.playlist.videoCount,
                videos = page.videos.items,
                isLoading = false,
                canLoadMore = page.videos.next != null,
            )
        } catch (failure: IOException) {
            Log.e(TAG, "Loading playlist $playlistUrl failed", failure)
            mutableState.update { it.copy(isLoading = false, hasError = true) }
        }
    }

    private suspend fun observeLocal(playlistId: Long) {
        combine(playlistDao.observePlaylists(), playlistDao.observeItems(playlistId)) { playlists, items ->
            val playlist = playlists.firstOrNull { it.id == playlistId }
            val videos = items.map { it.toVideoItem() }
            PlaylistState(
                title = if (playlist?.id == PlaylistEntity.LIKED_ID) likedPlaylistTitle else playlist?.name.orEmpty(),
                videoCount = videos.size.toLong(),
                videos = videos,
                isLoading = false,
            )
        }.collect { localState -> mutableState.value = localState }
    }

    private companion object {
        const val TAG = "PlaylistViewModel"
    }
}
