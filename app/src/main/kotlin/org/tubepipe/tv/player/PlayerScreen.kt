package org.tubepipe.tv.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tubepipe.core.database.PlaylistEntity
import org.tubepipe.core.service.VideoDetails
import org.tubepipe.core.ui.component.ErrorView
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.component.MediaRow
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.common.toCardModel
import org.tubepipe.tv.common.toPlaylistVideo
import org.tubepipe.tv.common.videoSubtitle
import org.tubepipe.tv.data.CaptionSize
import org.tubepipe.tv.navigation.AppNavigator

private const val OVERLAY_HIDE_DELAY_MILLIS = 4_000L
private const val SEEK_FEEDBACK_MILLIS = 1_500L
private const val RESTART_THRESHOLD_MILLIS = 5_000L
private val PanelBackground = Color(0xE6000000)
private val SpeedChoices = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** Which list the More button shows; Root has the entries, the others replace it. */
private enum class MoreStage { Root, Playlists, AudioTracks }

@Composable
fun PlayerScreen(videoUrl: String, container: AppContainer, navigator: AppNavigator) {
    val controller = container.playback
    val viewModel = rememberPlayerViewModel(videoUrl, container)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying by controller.state.collectAsStateWithLifecycle()
    val engine = (controller.engine.collectAsStateWithLifecycle().value) as? TubepipePlayerEngine
    val details = state.details
    // Content below installs its own BackHandler; this one covers loading and error.
    BackHandler { navigator.back() }

    LaunchedEffect(videoUrl) { controller.engineFor(videoUrl) }
    DisposableEffect(videoUrl) {
        onDispose { leavePlayer(controller, videoUrl, viewModel) }
    }

    val isFullscreenForThisVideo = nowPlaying == NowPlaying(videoUrl, PlayerMode.Fullscreen)
    when {
        state.hasError -> ErrorView(message = stringResource(R.string.error_network), onRetry = viewModel::retry)
        engine?.loadState == LoadState.Failed ->
            ErrorView(message = stringResource(R.string.player_error), onRetry = { controller.restart(videoUrl) })
        state.isLoading || details == null || engine == null || !isFullscreenForThisVideo -> LoadingView()
        else -> PlayerContent(details, state, viewModel, engine, container, navigator)
    }
}

/**
 * Leaving the screen (Back, or opening a channel) minimises the player instead of stopping it, unless there is
 * nothing to keep: the video failed to load. A different video that already took over is left alone.
 */
private fun leavePlayer(controller: PlaybackController, videoUrl: String, viewModel: PlayerViewModel) {
    if (controller.state.value?.videoUrl != videoUrl) {
        return
    }
    val engine = controller.currentEngine as? TubepipePlayerEngine
    val hasFailed = viewModel.state.value.hasError || engine?.loadState == LoadState.Failed
    if (hasFailed) controller.stop() else controller.minimize()
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    details: VideoDetails,
    state: PlayerState,
    viewModel: PlayerViewModel,
    engine: TubepipePlayerEngine,
    container: AppContainer,
    navigator: AppNavigator,
) {
    val controller = container.playback
    val snapshot = engine.snapshot
    val exoPlayer = engine.player?.exoPlayer
    var isBarVisible by remember { mutableStateOf(true) }
    var isPanelVisible by remember { mutableStateOf(false) }
    var isRelatedRowFocused by remember { mutableStateOf(false) }
    var isButtonRowFocused by remember { mutableStateOf(false) }
    var openList by remember { mutableStateOf<ControlList?>(null) }
    var moreStage by remember { mutableStateOf(MoreStage.Root) }
    var keyPressCount by remember { mutableIntStateOf(0) }
    var seekFeedbackCount by remember { mutableIntStateOf(0) }
    var isSeekFeedbackVisible by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }

    fun seekBy(deltaMillis: Long) {
        val upperBound = if (snapshot.durationMillis > 0) snapshot.durationMillis else Long.MAX_VALUE
        val target = (snapshot.positionMillis + deltaMillis).coerceIn(0L, upperBound)
        exoPlayer?.seekTo(target)
        snapshot.positionMillis = target
    }

    fun closeList() {
        openList = null
        moreStage = MoreStage.Root
    }

    val actions = ControlBarActions(
        onPrevious = {
            val previousUrl = controller.previousVideoUrl()
            if (snapshot.positionMillis > RESTART_THRESHOLD_MILLIS || previousUrl == null) {
                seekBy(-snapshot.positionMillis)
            } else {
                navigator.openVideo(previousUrl)
            }
        },
        onSeekBack = { seekBy(-state.seekStepMillis) },
        onPlayPause = controller::togglePause,
        onSeekForward = { seekBy(state.seekStepMillis) },
        onNext = { viewModel.nextVideoUrl()?.let(navigator::openVideo) },
        onOpenList = { list -> openList = list },
        onToggleLike = viewModel::toggleLike,
        onOpenChannel = { details.video.channelUrl?.let(navigator::openChannel) },
    )

    // The bar stays while paused, so the viewer can read it; it hides 4 s after the last key when playing.
    LaunchedEffect(isBarVisible, snapshot.isPlaying, keyPressCount, openList) {
        if (isBarVisible && snapshot.isPlaying && openList == null) {
            delay(OVERLAY_HIDE_DELAY_MILLIS)
            isBarVisible = false
        }
    }

    LaunchedEffect(seekFeedbackCount) {
        if (seekFeedbackCount > 0) {
            isSeekFeedbackVisible = true
            delay(SEEK_FEEDBACK_MILLIS)
            isSeekFeedbackVisible = false
        }
    }

    LaunchedEffect(isBarVisible, isPanelVisible) {
        when {
            isPanelVisible -> Unit
            isBarVisible -> playPauseFocus.requestFocus()
            else -> rootFocus.requestFocus()
        }
    }

    BackHandler {
        when {
            openList != null -> closeList()
            isPanelVisible -> isPanelVisible = false
            isBarVisible -> isBarVisible = false
            else -> navigator.back()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    keyPressCount += 1
                }
                false
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                if (isPanelVisible) {
                    // Only Up leaves the panel; from the Related row it moves focus up to the buttons instead.
                    if (event.key == Key.DirectionUp && !isRelatedRowFocused) {
                        isPanelVisible = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (isBarVisible) {
                    return@onKeyEvent when {
                        // With a list open, sideways keys must not move focus out of it.
                        openList != null -> event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                        event.key == Key.DirectionDown && isButtonRowFocused -> {
                            isPanelVisible = true
                            isBarVisible = false
                            true
                        }
                        else -> false
                    }
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        controller.togglePause()
                        isBarVisible = true
                        true
                    }
                    Key.DirectionLeft -> {
                        seekBy(-state.seekStepMillis)
                        seekFeedbackCount += 1
                        true
                    }
                    Key.DirectionRight -> {
                        seekBy(state.seekStepMillis)
                        seekFeedbackCount += 1
                        true
                    }
                    Key.DirectionDown -> {
                        isPanelVisible = true
                        true
                    }
                    Key.DirectionUp -> {
                        isBarVisible = true
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (exoPlayer != null) {
            PlayerSurface(
                player = exoPlayer,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (engine.loadState != LoadState.Loaded) {
            LoadingView()
        }
        CaptionText(
            text = snapshot.captionText,
            size = state.captionSize,
            isBarVisible = isBarVisible && !isPanelVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (isBarVisible && !isPanelVisible) {
            val listOptions = optionsFor(
                list = openList,
                moreStage = moreStage,
                onMoreStage = { newStage -> moreStage = newStage },
                onClose = ::closeList,
                engine = engine,
                details = details,
                container = container,
                navigator = navigator,
            )
            PlayerControlBar(
                details = details,
                snapshot = snapshot,
                engine = engine,
                state = state,
                openList = openList,
                listOptions = listOptions,
                listKey = openList to moreStage,
                playPauseFocus = playPauseFocus,
                onButtonRowFocusChanged = { isButtonRowFocused = it },
                actions = actions,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (isSeekFeedbackVisible && !isPanelVisible) {
            SeekFeedback(details, snapshot, state.segments, Modifier.align(Alignment.BottomCenter))
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
        snapshot.skippedCategory?.let { category ->
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
private fun optionsFor(
    list: ControlList?,
    moreStage: MoreStage,
    onMoreStage: (MoreStage) -> Unit,
    onClose: () -> Unit,
    engine: TubepipePlayerEngine,
    details: VideoDetails,
    container: AppContainer,
    navigator: AppNavigator,
): List<ListOption> = when (list) {
    null -> emptyList()
    ControlList.Speed -> SpeedChoices.map { speed ->
        ListOption(stringResource(R.string.player_speed_value, speedText(speed)), engine.currentSpeed == speed) {
            engine.setSpeed(speed)
            onClose()
        }
    }
    ControlList.Quality -> {
        val auto = ListOption(stringResource(R.string.player_quality_auto), engine.currentQuality == null) {
            engine.setQuality(null)
            onClose()
        }
        listOf(auto) + engine.qualityOptions().map { height ->
            ListOption(stringResource(R.string.player_quality_height, height), engine.currentQuality == height) {
                engine.setQuality(height)
                onClose()
            }
        }
    }
    ControlList.Captions -> {
        val off = ListOption(stringResource(R.string.player_captions_off), engine.captionLanguage == null) {
            engine.setCaptions(null)
            onClose()
        }
        listOf(off) + engine.captionOptions().map { option ->
            ListOption(option.label, engine.captionLanguage == option.language) {
                engine.setCaptions(option.language)
                onClose()
            }
        }
    }
    ControlList.More -> moreOptions(moreStage, onMoreStage, onClose, engine, details, container, navigator)
}

@Composable
private fun moreOptions(
    stage: MoreStage,
    onStage: (MoreStage) -> Unit,
    onClose: () -> Unit,
    engine: TubepipePlayerEngine,
    details: VideoDetails,
    container: AppContainer,
    navigator: AppNavigator,
): List<ListOption> {
    val scope = rememberCoroutineScope()
    val playlistDao = container.database.playlistDao()
    val playlists by playlistDao.observePlaylists().collectAsState(initial = emptyList())
    return when (stage) {
        MoreStage.Root -> buildList {
            details.video.channelUrl?.let { channelUrl ->
                add(
                    ListOption(stringResource(R.string.action_open_channel), false) {
                        navigator.openChannel(channelUrl)
                    },
                )
            }
            add(ListOption(stringResource(R.string.player_add_to_playlist), false) { onStage(MoreStage.Playlists) })
            if (engine.audioOptions().isNotEmpty()) {
                add(ListOption(stringResource(R.string.player_audio_track), false) { onStage(MoreStage.AudioTracks) })
            }
        }
        MoreStage.Playlists -> {
            val userPlaylists = playlists.filter { it.id != PlaylistEntity.LIKED_ID }
            if (userPlaylists.isEmpty()) {
                listOf(ListOption(stringResource(R.string.player_no_playlists), false) { onClose() })
            } else {
                userPlaylists.map { playlist ->
                    ListOption(playlist.name, false) {
                        scope.launch { playlistDao.addItem(playlist.id, details.video.toPlaylistVideo()) }
                        onClose()
                    }
                }
            }
        }
        MoreStage.AudioTracks -> engine.audioOptions().map { option ->
            ListOption(option.label, false) {
                engine.selectAudio(option)
                onClose()
            }
        }
    }
}

/** Plain-text captions in the Settings size; the bar lifts them out of its way. */
@Composable
private fun CaptionText(text: String, size: CaptionSize, isBarVisible: Boolean, modifier: Modifier = Modifier) {
    if (text.isEmpty()) {
        return
    }
    val fontSize = when (size) {
        CaptionSize.Small -> 22.sp
        CaptionSize.Normal -> 30.sp
        CaptionSize.Large -> 40.sp
    }
    Text(
        text = text,
        fontSize = fontSize,
        color = TubepipeColors.OnSurface,
        modifier = modifier
            .padding(bottom = if (isBarVisible) CaptionLiftedPadding else TubepipeSpacing.SafeMarginVertical * 2)
            .background(Color(0x99000000))
            .padding(horizontal = TubepipeSpacing.Gutter, vertical = TubepipeSpacing.Grid),
    )
}

private val CaptionLiftedPadding = 300.dp

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
