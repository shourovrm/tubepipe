package org.tubepipe.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.session.MediaSession
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.tubepipe.core.player.PlaybackRuntime
import org.tubepipe.core.player.PlayerClient
import org.tubepipe.core.player.TubepipePlayer
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.navigation.Screen

private const val OVERLAY_POLL_MILLIS = 250L
private const val SAVE_INTERVAL_MILLIS = 5_000L
private const val SKIPPED_LABEL_MILLIS = 2_500L

/** The view model of one video; the player screen and the channel bar share the instance by its key. */
@Composable
internal fun rememberPlayerViewModel(videoUrl: String, container: AppContainer): PlayerViewModel =
    viewModel(key = videoUrl) {
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

/**
 * Lives in the app shell, outside any screen, so playback goes on when the player screen is left: it loads the
 * video, keeps the media session, saves the position, skips SponsorBlock segments and starts the next video.
 * It also decides, from the screen stack, whether the video plays full screen, in the channel bar, or not at all.
 */
@Composable
fun PlaybackHost(container: AppContainer, navigator: AppNavigator) {
    val controller = container.playback
    val screen = navigator.current
    val isBarOverPage = navigator.isBarOverPage
    // Leaving the player stops the video, except for the channel page that opens over it and the playlist page
    // it was started from. The player screen itself takes the video back to full screen when it is shown again.
    LaunchedEffect(screen, isBarOverPage) {
        when {
            screen is Screen.Player -> Unit
            isBarOverPage -> controller.showAsBar()
            controller.state.value != null -> controller.stop()
        }
    }
    val nowPlaying by controller.state.collectAsStateWithLifecycle()
    val currentEngine by controller.engine.collectAsStateWithLifecycle()
    val current = nowPlaying ?: return
    val engine = currentEngine as? TubepipePlayerEngine ?: return
    // A new engine (another video, or a retry) starts a fresh session.
    key(engine) {
        PlaybackSession(current.videoUrl, engine, container, navigator)
    }
}

@Composable
private fun PlaybackSession(
    videoUrl: String,
    engine: TubepipePlayerEngine,
    container: AppContainer,
    navigator: AppNavigator,
) {
    val context = LocalContext.current
    val controller = container.playback
    val viewModel = rememberPlayerViewModel(videoUrl, container)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A video hidden by parental controls is never loaded.
    val hasDetails = state.details != null && !state.isBlockedAsAdult

    LaunchedEffect(engine, hasDetails) {
        if (hasDetails && engine.loadState == LoadState.Idle) {
            PlaybackRuntime.initialize(context)
            val clientName = container.settings.settings.first().playerClientName
            val client = PlayerClient.entries.firstOrNull { it.extractorName == clientName } ?: PlayerClient.DEFAULT
            engine.load(
                client = client,
                preferences = viewModel.playbackPreferences(),
                videoUrl = videoUrl,
                startPositionMillis = viewModel.state.value.resumePositionMillis ?: 0L,
            )
        }
    }

    // The player screen shows its own error; a bar that fails has nobody to show it to.
    val hasFailed = state.hasError || engine.loadState == LoadState.Failed
    LaunchedEffect(hasFailed) {
        if (hasFailed && controller.state.value?.mode == PlayerMode.Bar) {
            controller.stop()
        }
    }

    val player = engine.player
    if (player != null) {
        PlayerBinding(videoUrl, player, engine.snapshot, viewModel, container, navigator)
    }
}

/** Listener, media session, position poll and periodic save for one [TubepipePlayer]. */
@Composable
private fun PlayerBinding(
    videoUrl: String,
    player: TubepipePlayer,
    snapshot: PlaybackSnapshot,
    viewModel: PlayerViewModel,
    container: AppContainer,
    navigator: AppNavigator,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Runs after the video ended. Only the full-screen player moves on: a bar on a channel page must not pull
    // the viewer away from the channel they are browsing. A playlist bar plays the next entry in the bar.
    fun playAfterEnd() {
        if (navigator.minimizedQueue != null) {
            if (navigator.advanceMinimizedQueue()) {
                val nextUrl = navigator.minimizedQueue?.currentUrl ?: return
                container.playback.engineFor(nextUrl)
                container.playback.showAsBar()
            }
            return
        }
        val screen = navigator.current as? Screen.Player ?: return
        val queue = screen.queue
        if (queue != null) {
            // A playlist plays through even with Autoplay off; the last entry just stops.
            queue.next()?.let(navigator::playQueue)
        } else {
            viewModel.videoToAutoplayAfterEnd()?.let(navigator::openVideo)
        }
    }

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

            override fun onCues(cueGroup: CueGroup) {
                snapshot.captionText = cueGroup.cues.joinToString(separator = "\n") { it.text?.toString().orEmpty() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    snapshot.hasStarted = true
                }
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.savePosition(exoPlayer.duration.coerceAtLeast(0L))
                    // Releasing a player from inside its own callback is unsafe, so the switch runs afterwards.
                    scope.launch { playAfterEnd() }
                }
            }
        }
        exoPlayer.addListener(listener)
        // Unique id: the next video's session is created before this one is released.
        val mediaSession = MediaSession.Builder(context, exoPlayer).setId(UUID.randomUUID().toString()).build()
        onDispose {
            // The engine may already be released, so the last polled position is saved, not the player's own.
            if (snapshot.hasStarted) {
                viewModel.savePosition(snapshot.positionMillis)
            }
            view.keepScreenOn = false
            exoPlayer.removeListener(listener)
            mediaSession.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val exoPlayer = player.exoPlayer
            val position = exoPlayer.currentPosition
            snapshot.positionMillis = position
            snapshot.durationMillis = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            val skipTarget = if (exoPlayer.isPlaying) viewModel.skipTargetFor(position) else null
            if (skipTarget != null) {
                if (viewModel.state.value.showSkipNotice) {
                    snapshot.skippedCategory =
                        viewModel.state.value.segments.firstOrNull { it.endMillis == skipTarget }?.category
                }
                exoPlayer.seekTo(skipTarget)
            }
            delay(OVERLAY_POLL_MILLIS)
        }
    }

    LaunchedEffect(snapshot.skippedCategory) {
        if (snapshot.skippedCategory != null) {
            delay(SKIPPED_LABEL_MILLIS)
            snapshot.skippedCategory = null
        }
    }

    LaunchedEffect(player, videoUrl) {
        while (true) {
            delay(SAVE_INTERVAL_MILLIS)
            if (snapshot.hasStarted && snapshot.isPlaying) {
                viewModel.savePosition(snapshot.positionMillis)
            }
        }
    }
}
