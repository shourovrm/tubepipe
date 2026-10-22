package org.tubepipe.tv.channel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tubepipe.core.database.SubscriptionDao
import org.tubepipe.core.database.SubscriptionEntity
import org.tubepipe.core.service.ChannelSummary
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.PageCursor
import org.tubepipe.core.service.VideoItem

data class ChannelState(
    val channel: ChannelSummary? = null,
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val isSubscribed: Boolean = false,
    val canLoadMore: Boolean = false,
)

class ChannelViewModel(
    private val channelUrl: String,
    private val content: ContentRepository,
    private val subscriptions: SubscriptionDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChannelState())
    val state: StateFlow<ChannelState> = mutableState

    private var nextPageCursor: PageCursor? = null
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            subscriptions.observeIsSubscribed(channelUrl).collect { isSubscribed ->
                mutableState.update { it.copy(isSubscribed = isSubscribed) }
            }
        }
        load()
    }

    fun retry() = load()

    fun loadMore() {
        val cursor = nextPageCursor ?: return
        if (loadMoreJob?.isActive == true || mutableState.value.isLoading) {
            return
        }
        loadMoreJob = viewModelScope.launch {
            try {
                val page = content.channelVideosMore(channelUrl, cursor)
                nextPageCursor = page.next
                mutableState.update {
                    it.copy(videos = it.videos + page.items, canLoadMore = page.next != null)
                }
            } catch (failure: IOException) {
                // Keep the videos already shown and the cursor, so a later scroll can try again.
                Log.w(TAG, "Loading more videos for $channelUrl failed", failure)
            }
        }
    }

    fun toggleSubscription() {
        val current = mutableState.value
        val channel = current.channel ?: return
        viewModelScope.launch {
            if (current.isSubscribed) {
                subscriptions.delete(channelUrl)
            } else {
                subscriptions.upsert(
                    SubscriptionEntity(
                        url = channelUrl,
                        name = channel.name,
                        avatarUrl = channel.avatarUrl,
                        subscribedAtMillis = clock(),
                    ),
                )
            }
        }
    }

    private fun load() {
        loadMoreJob?.cancel()
        mutableState.update { it.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            try {
                val page = content.channel(channelUrl)
                nextPageCursor = page.videos.next
                mutableState.update {
                    it.copy(
                        channel = page.channel,
                        videos = page.videos.items,
                        isLoading = false,
                        canLoadMore = page.videos.next != null,
                    )
                }
            } catch (failure: IOException) {
                Log.e(TAG, "Loading channel $channelUrl failed", failure)
                mutableState.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }

    private companion object {
        const val TAG = "ChannelViewModel"
    }
}
