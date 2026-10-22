package org.tubepipe.tv.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.service.ChannelSummary
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.EmptyView
import org.tubepipe.core.ui.component.ErrorView
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.component.VideoGrid
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.common.CountFormatting
import org.tubepipe.tv.common.VideoActionsDialog
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.player.PlayerBar

private val AvatarSize = 96.dp

@Composable
fun ChannelScreen(channelUrl: String, container: AppContainer, navigator: AppNavigator) {
    val viewModel = viewModel(key = channelUrl) {
        ChannelViewModel(channelUrl, container.content, container.database.subscriptionDao())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionsVideo by remember { mutableStateOf<VideoItem?>(null) }
    // toCardModel needs composition (plural strings) but VideoGrid asks for models outside it, so build them here.
    val cardModels = state.videos.associate { video -> video.url to video.toCardModel() }

    Column(modifier = Modifier.fillMaxSize()) {
        val channel = state.channel
        if (channel != null) {
            ChannelHeader(
                channel = channel,
                isSubscribed = state.isSubscribed,
                onToggleSubscription = viewModel::toggleSubscription,
            )
        }
        when {
            state.isLoading -> LoadingView()
            state.hasError -> ErrorView(
                message = stringResource(R.string.error_network),
                onRetry = viewModel::retry,
            )
            state.videos.isEmpty() -> EmptyView(message = stringResource(R.string.channel_no_videos))
            else -> VideoGrid(
                items = state.videos,
                itemKey = { video -> video.url },
                cardModel = { video -> cardModels.getValue(video.url) },
                onItemClick = { video -> navigator.openVideo(video.url) },
                onItemLongClick = { video -> actionsVideo = video },
                onNearEnd = if (state.canLoadMore) viewModel::loadMore else null,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        // Only a channel opened from the playing video has one; Down from the grid reaches it.
        if (navigator.isChannelOverPlayer) {
            PlayerBar(container, navigator)
        }
    }

    actionsVideo?.let { video ->
        VideoActionsDialog(
            video = video,
            playlists = container.database.playlistDao(),
            onPlay = { navigator.openVideo(video.url) },
            onOpenChannel = navigator::openChannel,
            onDismiss = { actionsVideo = null },
        )
    }
}

@Composable
private fun ChannelHeader(channel: ChannelSummary, isSubscribed: Boolean, onToggleSubscription: () -> Unit) {
    val subscribeFocus = remember { FocusRequester() }
    // The Row spans the full width and is a focus group, so Up from any grid column lands on the button.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .padding(
                horizontal = TubepipeSpacing.SafeMarginHorizontal,
                vertical = TubepipeSpacing.SafeMarginVertical,
            ),
    ) {
        ChannelAvatar(avatarUrl = channel.avatarUrl, size = AvatarSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.headlineMedium,
                color = TubepipeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subscriberCount = channel.subscriberCount
            if (subscriberCount != null) {
                Text(
                    text = pluralStringResource(
                        R.plurals.channel_subscriber_count,
                        subscriberCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        CountFormatting.compact(subscriberCount),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TubepipeColors.OnSurfaceVariant,
                )
            }
        }
        SubscribeButton(
            isSubscribed = isSubscribed,
            onClick = onToggleSubscription,
            modifier = Modifier.focusRequester(subscribeFocus),
        )
    }
    // Landing on the header puts the remote on the one action this screen offers before the grid.
    LaunchedEffect(Unit) { subscribeFocus.requestFocus() }
}

@Composable
private fun SubscribeButton(isSubscribed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor = if (isSubscribed) TubepipeColors.SurfaceVariant else TubepipeColors.Primary
    val labelRes = if (isSubscribed) R.string.channel_subscribed else R.string.channel_subscribe
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = containerColor,
            contentColor = TubepipeColors.OnPrimary,
            focusedContainerColor = containerColor,
            focusedContentColor = TubepipeColors.OnPrimary,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder)),
        ),
        modifier = modifier,
    ) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
    }
}
