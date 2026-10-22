package org.tubepipe.tv.subscriptions

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
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.VideoItem

data class SubscriptionsState(
    val channels: List<SubscriptionEntity> = emptyList(),
    val feed: List<VideoItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
)

class SubscriptionsViewModel(private val content: ContentRepository, private val subscriptionDao: SubscriptionDao) :
    ViewModel() {
    private val mutableState = MutableStateFlow(SubscriptionsState())
    val state: StateFlow<SubscriptionsState> = mutableState

    private var feedJob: Job? = null

    init {
        viewModelScope.launch {
            subscriptionDao.observeAll().collect { channels ->
                mutableState.update { it.copy(channels = channels) }
                refreshFeed(channels)
            }
        }
    }

    fun retry() = refreshFeed(mutableState.value.channels)

    private fun refreshFeed(channels: List<SubscriptionEntity>) {
        feedJob?.cancel()
        if (channels.isEmpty()) {
            // Nothing to ask YouTube about, so the empty state appears without a network call.
            mutableState.update { it.copy(feed = emptyList(), isLoading = false, hasError = false) }
            return
        }
        mutableState.update { it.copy(isLoading = true, hasError = false) }
        feedJob = viewModelScope.launch {
            try {
                val feed = content.latestFromChannels(channels.map { it.url })
                mutableState.update { it.copy(feed = feed, isLoading = false) }
            } catch (failure: IOException) {
                Log.e(TAG, "Loading the subscription feed failed", failure)
                mutableState.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }

    private companion object {
        const val TAG = "SubscriptionsViewModel"
    }
}
