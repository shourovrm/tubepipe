package org.tubepipe.tv.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tubepipe.core.database.HistoryDao
import org.tubepipe.core.database.PlaylistDao
import org.tubepipe.core.database.PlaylistEntity
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.VideoDetails
import org.tubepipe.tv.common.toHistoryEntity
import org.tubepipe.tv.common.toPlaylistVideo
import org.tubepipe.tv.data.AppSettings

data class PlayerState(
    val details: VideoDetails? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    /** Where to start; null when the video was never watched or is at least 95% watched. */
    val resumePositionMillis: Long? = null,
    val isLiked: Boolean = false,
    val segments: List<SkipSegment> = emptyList(),
)

class PlayerViewModel(
    private val videoUrl: String,
    private val content: ContentRepository,
    private val history: HistoryDao,
    private val playlists: PlaylistDao,
    private val settings: Flow<AppSettings>,
    private val sponsorBlock: SponsorBlockSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = mutableState

    private var isSponsorBlockEnabled = false

    init {
        viewModelScope.launch {
            playlists.observeContains(PlaylistEntity.LIKED_ID, videoUrl).collect { isLiked ->
                mutableState.update { it.copy(isLiked = isLiked) }
            }
        }
        load()
    }

    fun retry() = load()

    /**
     * Stores [positionMillis] as the resume point. The view model outlives the screen (it is keyed by URL in the
     * activity's store), so the in-memory resume position is updated too; reopening the video then starts there.
     */
    fun savePosition(positionMillis: Long) {
        val details = mutableState.value.details ?: return
        mutableState.update {
            it.copy(resumePositionMillis = resumePositionFor(positionMillis, details.video.durationSeconds))
        }
        viewModelScope.launch {
            history.savePosition(videoUrl, positionMillis, clock())
        }
    }

    fun toggleLike() {
        val current = mutableState.value
        val details = current.details ?: return
        viewModelScope.launch {
            if (current.isLiked) {
                playlists.removeItem(PlaylistEntity.LIKED_ID, videoUrl)
            } else {
                playlists.addItem(PlaylistEntity.LIKED_ID, details.video.toPlaylistVideo())
            }
        }
    }

    /** The position to seek to when [positionMillis] is inside a skip segment, else null. */
    fun skipTargetFor(positionMillis: Long): Long? {
        if (!isSponsorBlockEnabled) {
            return null
        }
        return mutableState.value.segments
            .firstOrNull { positionMillis >= it.startMillis && positionMillis < it.endMillis }
            ?.endMillis
    }

    private fun load() {
        mutableState.update { it.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            try {
                val details = content.videoDetails(videoUrl)
                // Read before the upsert below, which rewrites the row.
                val previousPosition = history.positionOf(videoUrl)
                isSponsorBlockEnabled = settings.first().sponsorBlockEnabled
                history.upsert(details.video.toHistoryEntity(clock(), positionMillis = previousPosition ?: 0L))
                mutableState.update {
                    it.copy(
                        details = details,
                        isLoading = false,
                        resumePositionMillis = previousPosition?.let { position ->
                            resumePositionFor(position, details.video.durationSeconds)
                        },
                    )
                }
                if (isSponsorBlockEnabled) {
                    loadSegments()
                }
            } catch (failure: IOException) {
                Log.e(TAG, "Loading video $videoUrl failed", failure)
                mutableState.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }

    private suspend fun loadSegments() {
        val videoId = YoutubeVideoId.from(videoUrl) ?: return
        try {
            val segments = sponsorBlock.segments(videoId)
            mutableState.update { it.copy(segments = segments) }
        } catch (failure: IOException) {
            // Playback must not depend on a community service; the video plays without skipping.
            Log.w(TAG, "Loading SponsorBlock segments for $videoId failed", failure)
        }
    }

    private fun resumePositionFor(positionMillis: Long, durationSeconds: Long?): Long? {
        if (positionMillis <= 0) {
            return null
        }
        if (durationSeconds != null && durationSeconds > 0) {
            val watchedFraction = positionMillis / (durationSeconds * MILLIS_PER_SECOND.toDouble())
            if (watchedFraction >= FINISHED_FRACTION) {
                return null
            }
        }
        return positionMillis
    }

    private companion object {
        const val TAG = "PlayerViewModel"
        const val FINISHED_FRACTION = 0.95
        const val MILLIS_PER_SECOND = 1_000
    }
}
