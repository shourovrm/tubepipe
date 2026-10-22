package org.tubepipe.tv

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tubepipe.core.database.TubepipeDatabase
import org.tubepipe.core.player.PlaybackRuntime
import org.tubepipe.core.player.PlayerClient
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.StreamInfoCache
import org.tubepipe.core.service.YoutubeContentRepository
import org.tubepipe.tv.data.FilteringContentRepository
import org.tubepipe.tv.data.SettingsRepository
import org.tubepipe.tv.home.HomeRowsMemory
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.player.PlaybackController
import org.tubepipe.tv.player.PrefetchScheduler
import org.tubepipe.tv.player.TubepipePlayerEngine

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual dependency wiring (docs/decisions/architecture.md: no Hilt until 40+ bindings). One per process. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val database: TubepipeDatabase = TubepipeDatabase.create(applicationContext)
    val settings: SettingsRepository = SettingsRepository(applicationContext.settingsDataStore)

    // Every screen reads YouTube through the Settings content filters (Shorts, upcoming, members-only).
    val content: ContentRepository = FilteringContentRepository(YoutubeContentRepository(), settings.settings)

    /** The one running player; it outlives the player screen so the video continues in the channel bar. */
    val playback = PlaybackController(createEngine = { TubepipePlayerEngine(applicationContext) })

    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // isCached is not a suspend function, so the setting is mirrored here instead of read from DataStore on Main.
    @Volatile
    private var preferredClientName = PlayerClient.DEFAULT.extractorName

    /** Fetches a video's stream info ahead of the viewer pressing Centre; see [PrefetchScheduler] for the limits. */
    val prefetchScheduler = PrefetchScheduler(
        scope = prefetchScope,
        prefetch = ::prefetchVideo,
        isCached = ::isVideoPrefetched,
        clock = System::currentTimeMillis,
    )

    init {
        prefetchScope.launch {
            settings.settings.collect { latest -> preferredClientName = latest.playerClientName }
        }
    }

    /** The player client chosen in Settings; an unknown stored name falls back to the default. */
    suspend fun preferredPlayerClient(): PlayerClient {
        val clientName = settings.settings.first().playerClientName
        return PlayerClient.entries.firstOrNull { client -> client.extractorName == clientName } ?: PlayerClient.DEFAULT
    }

    private suspend fun prefetchVideo(videoUrl: String) {
        // Starts the WebView decoder and PO-token provider, the slow cold part of the first play. It needs the
        // main thread and does nothing on later calls.
        withContext(Dispatchers.Main) { PlaybackRuntime.initialize(applicationContext) }
        val client = preferredPlayerClient()
        try {
            StreamInfoCache.prefetch(videoUrl, playerClientName = null)
            StreamInfoCache.prefetch(videoUrl, client.extractorName)
        } catch (failure: IOException) {
            // Only the head start is lost: pressing Centre fetches as it did before prefetching.
            Log.w(TAG, "Prefetching $videoUrl failed", failure)
        }
    }

    private fun isVideoPrefetched(videoUrl: String): Boolean =
        StreamInfoCache.contains(videoUrl, playerClientName = null) &&
            StreamInfoCache.contains(videoUrl, preferredClientName)

    /**
     * The screen stack lives with the process, not the activity: Android destroys and recreates the activity
     * while the app is in the background (the Mi Box has 2 GB), and a stack owned by it would reset to Home.
     */
    val navigator = AppNavigator()

    /** Last rows Home showed, so a recreated Home screen is not reduced to one saved row while it reloads. */
    val homeRowsMemory = HomeRowsMemory()

    private companion object {
        const val TAG = "AppContainer"
    }
}
