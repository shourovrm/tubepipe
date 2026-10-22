package org.tubepipe.tv.subscriptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.tubepipe.core.database.SubscriptionEntity
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.EmptyView
import org.tubepipe.core.ui.component.ErrorView
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.component.VideoGrid
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.channel.ChannelAvatar
import org.tubepipe.tv.common.VideoActionsDialog
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.navigation.AppNavigator

private val ChannelItemWidth = 120.dp
private val ChannelAvatarSize = 80.dp

@Composable
fun SubscriptionsScreen(container: AppContainer, navigator: AppNavigator) {
    val viewModel = viewModel {
        SubscriptionsViewModel(container.content, container.database.subscriptionDao())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionsVideo by remember { mutableStateOf<VideoItem?>(null) }
    // toCardModel needs composition (plural strings) but VideoGrid asks for models outside it, so build them here.
    val cardModels = state.feed.associate { video -> video.url to video.toCardModel() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.channels.isNotEmpty()) {
            ChannelRow(channels = state.channels, onChannelClick = { channel -> navigator.openChannel(channel.url) })
        }
        val contentModifier = Modifier.weight(1f).fillMaxWidth()
        when {
            state.isLoading && state.channels.isEmpty() -> LoadingView(modifier = contentModifier)
            state.channels.isEmpty() -> EmptyView(
                message = stringResource(R.string.subscriptions_empty),
                modifier = contentModifier,
            )
            state.isLoading -> LoadingView(modifier = contentModifier)
            state.hasError -> ErrorView(
                message = stringResource(R.string.error_network),
                onRetry = viewModel::retry,
                modifier = contentModifier,
            )
            state.feed.isEmpty() -> EmptyView(
                message = stringResource(R.string.subscriptions_feed_empty),
                modifier = contentModifier,
            )
            else -> VideoGrid(
                items = state.feed,
                itemKey = { video -> video.url },
                cardModel = { video -> cardModels.getValue(video.url) },
                onItemClick = { video -> navigator.openVideo(video.url) },
                onItemLongClick = { video -> actionsVideo = video },
                modifier = contentModifier,
            )
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
private fun ChannelRow(channels: List<SubscriptionEntity>, onChannelClick: (SubscriptionEntity) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
        // Vertical room so the focus scale and border are not clipped by the row bounds.
        contentPadding = PaddingValues(
            horizontal = TubepipeSpacing.SafeMarginHorizontal,
            vertical = TubepipeSpacing.SafeMarginVertical,
        ),
    ) {
        items(channels, key = { channel -> channel.url }) { channel ->
            ChannelItem(channel = channel, onClick = { onChannelClick(channel) })
        }
    }
}

@Composable
private fun ChannelItem(channel: SubscriptionEntity, onClick: () -> Unit) {
    val shape = RoundedCornerShape(TubepipeSpacing.CardRadius)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, TubepipeColors.FocusBorder), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TubepipeColors.Surface,
            focusedContainerColor = TubepipeColors.Surface,
        ),
        modifier = Modifier.width(ChannelItemWidth),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(TubepipeSpacing.Grid * 2),
        ) {
            ChannelAvatar(avatarUrl = channel.avatarUrl, size = ChannelAvatarSize)
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TubepipeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TubepipeSpacing.Grid * 2),
            )
        }
    }
}
