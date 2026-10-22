package org.tubepipe.tv.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.tubepipe.core.player.PlaybackRuntime
import org.tubepipe.core.player.PlayerClient
import org.tubepipe.core.player.TubepipePlayer
import org.tubepipe.core.service.VideoDetails
import org.tubepipe.core.ui.component.ErrorView
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.component.MediaRow
import org.tubepipe.core.ui.component.TimeFormatting
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.common.videoSubtitle
import org.tubepipe.tv.navigation.AppNavigator

private const val TAG = "PlayerScreen"
private const val SEEK_STEP_MILLIS = 10_000L
private const val OVERLAY_HIDE_DELAY_MILLIS = 4_000L
private const val POSITION_POLL_MILLIS = 250L
private const val SAVE_INTERVAL_MILLIS = 5_000L
private const val SKIPPED_LABEL_MILLIS = 2_500L
private val PanelBackground = Color(0xE6000000)

@Composable
fun PlayerScreen(videoUrl: String, container: AppContainer, navigator: AppNavigator) {
    val viewModel = viewModel(key = videoUrl) {
        val database = container.database
        PlayerViewModel(
            videoUrl = videoUrl,
            content = container.content,
            history = database.historyDao(),
            playlists = database.playlistDao(),
            settings = container.settings.settings,
            sponsorBlock = SponsorBlockClient(),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    // Content screens below install their own BackHandler; this one covers loading and error.
    BackHandler { navigator.back() }

    when {
        state.hasError -> ErrorView(message = stringResource(R.string.error_network), onRetry = viewModel::retry)
        state.isLoading || details == null -> LoadingView()
        else -> PlayerContent(videoUrl, details, viewModel, container, navigator)
    }
}

/** What the screen draws from the player; written by the player listener and the position poll. */
@Stable
private class PlaybackSnapshot {
    var isPlaying by mutableStateOf(false)
    var playWhenReady by mutableStateOf(false)
    var positionMillis by mutableLongStateOf(0L)
    var durationMillis by mutableLongStateOf(0L)

    /** True once the player was ready at least once; only then is its position worth saving. */
    var hasStarted = false
}

@Composable
private fun PlayerContent(
    videoUrl: String,
    details: VideoDetails,
    viewModel: PlayerViewModel,
    container: AppContainer,
    navigator: AppNavigator,
) {
    val context = LocalContext.current
    val preferredClient by produceState<PlayerClient?>(initialValue = null) {
        val clientName = container.settings.settings.first().playerClientName
        value = PlayerClient.entries.firstOrNull { it.extractorName == clientName } ?: PlayerClient.DEFAULT
    }
    val client = preferredClient
    if (client == null) {
        LoadingView()
        return
    }

    var attempt by remember { mutableIntStateOf(0) }
    var hasPlaybackFailed by remember { mutableStateOf(false) }
    if (hasPlaybackFailed) {
        BackHandler { navigator.back() }
        ErrorView(
            message = stringResource(R.string.player_error),
            onRetry = {
                hasPlaybackFailed = false
                attempt += 1
            },
        )
        return
    }

    PlaybackRuntime.initialize(context)
    val player = remember(videoUrl, attempt) { TubepipePlayer(context, client) }
    val snapshot = remember(player) { PlaybackSnapshot() }
    var isStreamLoaded by remember(player) { mutableStateOf(false) }
    val startPositionMillis = remember(player) { viewModel.state.value.resumePositionMillis ?: 0L }

    PlayerLifecycle(player, snapshot, viewModel, details, navigator)

    LaunchedEffect(player) {
        try {
            player.load(videoUrl, startPositionMillis)
            isStreamLoaded = true
        } catch (failure: IOException) {
            Log.e(TAG, "Playing $videoUrl failed", failure)
            hasPlaybackFailed = true
        }
    }

    PlayerControls(player.exoPlayer, snapshot, isStreamLoaded, details, viewModel, navigator)
}

/** Listener, media session, periodic position save and release; everything tied to one [TubepipePlayer]. */
@Composable
private fun PlayerLifecycle(
    player: TubepipePlayer,
    snapshot: PlaybackSnapshot,
    viewModel: PlayerViewModel,
    details: VideoDetails,
    navigator: AppNavigator,
) {
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(player) {
        val exoPlayer = player.exoPlayer
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                snapshot.isPlaying = isPlaying
                view.keepScreenOn = isPlaying
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                snapshot.playWhenReady = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    snapshot.hasStarted = true
                }
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.savePosition(exoPlayer.duration.coerceAtLeast(0L))
                    details.related.firstOrNull()?.let { next -> navigator.openVideo(next.url) }
                }
            }
        }
        exoPlayer.addListener(listener)
        // Unique id: the next video's session is created before this one is released.
        val mediaSession = MediaSession.Builder(context, exoPlayer).setId(UUID.randomUUID().toString()).build()
        onDispose {
            if (snapshot.hasStarted) {
                viewModel.savePosition(exoPlayer.currentPosition)
            }
            view.keepScreenOn = false
            exoPlayer.removeListener(listener)
            mediaSession.release()
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(SAVE_INTERVAL_MILLIS)
            if (snapshot.hasStarted && snapshot.isPlaying) {
                viewModel.savePosition(player.exoPlayer.currentPosition)
            }
        }
    }
}

@Composable
private fun PlayerControls(
    player: Player,
    snapshot: PlaybackSnapshot,
    isStreamLoaded: Boolean,
    details: VideoDetails,
    viewModel: PlayerViewModel,
    navigator: AppNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isOverlayVisible by remember { mutableStateOf(true) }
    var isPanelVisible by remember { mutableStateOf(false) }
    var isRelatedRowFocused by remember { mutableStateOf(false) }
    var keyPressCount by remember { mutableIntStateOf(0) }
    var skippedCategory by remember { mutableStateOf<String?>(null) }
    val rootFocus = remember { FocusRequester() }

    fun seekBy(deltaMillis: Long) {
        val upperBound = if (snapshot.durationMillis > 0) snapshot.durationMillis else Long.MAX_VALUE
        val target = (player.currentPosition + deltaMillis).coerceIn(0L, upperBound)
        player.seekTo(target)
        snapshot.positionMillis = target
    }

    fun togglePlayPause() {
        if (player.playWhenReady) player.pause() else player.play()
    }

    // The overlay stays while paused, so the user can read the time; it hides 4 s after the last key when playing.
    LaunchedEffect(isOverlayVisible, snapshot.isPlaying, keyPressCount) {
        if (isOverlayVisible && snapshot.isPlaying) {
            delay(OVERLAY_HIDE_DELAY_MILLIS)
            isOverlayVisible = false
        }
    }

    LaunchedEffect(skippedCategory) {
        if (skippedCategory != null) {
            delay(SKIPPED_LABEL_MILLIS)
            skippedCategory = null
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val position = player.currentPosition
            snapshot.positionMillis = position
            snapshot.durationMillis = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            val skipTarget = if (player.isPlaying) viewModel.skipTargetFor(position) else null
            if (skipTarget != null) {
                skippedCategory = viewModel.state.value.segments.firstOrNull { it.endMillis == skipTarget }?.category
                player.seekTo(skipTarget)
            }
            delay(POSITION_POLL_MILLIS)
        }
    }

    LaunchedEffect(isPanelVisible) {
        if (!isPanelVisible) {
            rootFocus.requestFocus()
        }
    }

    BackHandler {
        when {
            isPanelVisible -> isPanelVisible = false
            isOverlayVisible -> isOverlayVisible = false
            else -> navigator.back()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                keyPressCount += 1
                if (isPanelVisible) {
                    // Only Up leaves the panel; from the Related row it moves focus up to the buttons instead.
                    if (event.key == Key.DirectionUp && !isRelatedRowFocused) {
                        isPanelVisible = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        togglePlayPause()
                        isOverlayVisible = true
                        true
                    }
                    Key.DirectionLeft -> {
                        seekBy(-SEEK_STEP_MILLIS)
                        isOverlayVisible = true
                        true
                    }
                    Key.DirectionRight -> {
                        seekBy(SEEK_STEP_MILLIS)
                        isOverlayVisible = true
                        true
                    }
                    Key.DirectionDown -> {
                        isPanelVisible = true
                        isOverlayVisible = false
                        true
                    }
                    Key.DirectionUp -> {
                        isOverlayVisible = true
                        true
                    }
                    else -> false
                }
            },
    ) {
        PlayerSurface(player = player, surfaceType = SURFACE_TYPE_SURFACE_VIEW, modifier = Modifier.fillMaxSize())

        if (!isStreamLoaded) {
            LoadingView()
        }
        if (isOverlayVisible && !isPanelVisible) {
            PlayerOverlay(
                details = details,
                snapshot = snapshot,
                segments = state.segments,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (isPanelVisible) {
            PlayerPanel(
                details = details,
                isLiked = state.isLiked,
                onToggleLike = viewModel::toggleLike,
                onOpenChannel = { channelUrl -> navigator.openChannel(channelUrl) },
                onOpenVideo = { videoUrl -> navigator.openVideo(videoUrl) },
                onRelatedFocusChanged = { isRelatedRowFocused = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        skippedCategory?.let { category ->
            Text(
                text = stringResource(R.string.player_skipped, segmentName(category)),
                style = MaterialTheme.typography.titleLarge,
                color = TubepipeColors.OnSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(TubepipeSpacing.SafeMarginHorizontal, TubepipeSpacing.SafeMarginVertical)
                    .background(PanelBackground)
                    .padding(horizontal = TubepipeSpacing.Gutter, vertical = TubepipeSpacing.Grid * 3),
            )
        }
    }
}

@Composable
private fun segmentName(category: String): String = stringResource(
    when (category) {
        "sponsor" -> R.string.player_segment_sponsor
        "intro" -> R.string.player_segment_intro
        "outro" -> R.string.player_segment_outro
        "interaction" -> R.string.player_segment_interaction
        else -> R.string.player_segment_other
    },
)

@Composable
private fun PlayerOverlay(
    details: VideoDetails,
    snapshot: PlaybackSnapshot,
    segments: List<SkipSegment>,
    modifier: Modifier = Modifier,
) {
    val durationMillis = if (snapshot.durationMillis > 0) {
        snapshot.durationMillis
    } else {
        (details.video.durationSeconds ?: 0L) * 1_000L
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelBackground)
            .padding(
                horizontal = TubepipeSpacing.SafeMarginHorizontal,
                vertical = TubepipeSpacing.SafeMarginVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 2),
    ) {
        Text(
            text = details.video.title,
            style = MaterialTheme.typography.headlineMedium,
            color = TubepipeColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = details.video.channelName,
            style = MaterialTheme.typography.titleMedium,
            color = TubepipeColors.OnSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter),
        ) {
            Text(
                text = stringResource(if (snapshot.playWhenReady) R.string.player_playing else R.string.player_paused),
                style = MaterialTheme.typography.titleMedium,
                color = TubepipeColors.OnSurface,
            )
            PlayerSeekBar(
                positionMillis = snapshot.positionMillis,
                durationMillis = durationMillis,
                segments = segments,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = TimeFormatting.duration(snapshot.positionMillis / 1_000) + " / " +
                    TimeFormatting.duration(durationMillis / 1_000),
                style = MaterialTheme.typography.titleMedium,
                color = TubepipeColors.OnSurface,
            )
        }
    }
}

@Composable
private fun PlayerPanel(
    details: VideoDetails,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onRelatedFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstButtonFocus = remember { FocusRequester() }
    // toCardModel needs composition (plural strings) but MediaRow asks for models outside it, so build them here.
    val cardModels = details.related.associate { video -> video.url to video.toCardModel() }
    val channelUrl = details.video.channelUrl
    LaunchedEffect(Unit) { firstButtonFocus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelBackground)
            .padding(top = TubepipeSpacing.SafeMarginVertical),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = TubepipeSpacing.SafeMarginHorizontal),
            verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 2),
        ) {
            Text(
                text = details.video.title,
                style = MaterialTheme.typography.headlineMedium,
                color = TubepipeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = videoSubtitle(details.video),
                style = MaterialTheme.typography.titleMedium,
                color = TubepipeColors.OnSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Gutter)) {
                Button(
                    onClick = { channelUrl?.let(onOpenChannel) },
                    enabled = channelUrl != null,
                    modifier = Modifier.focusRequester(firstButtonFocus),
                ) {
                    Text(text = details.video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = onToggleLike) {
                    Text(text = stringResource(if (isLiked) R.string.player_liked else R.string.player_like))
                }
            }
        }
        if (details.related.isNotEmpty()) {
            MediaRow(
                title = stringResource(R.string.player_related),
                items = details.related,
                itemKey = { video -> video.url },
                cardModel = { video -> cardModels.getValue(video.url) },
                onItemClick = { video -> onOpenVideo(video.url) },
                modifier = Modifier.onFocusChanged { focusState -> onRelatedFocusChanged(focusState.hasFocus) },
            )
        }
    }
}
