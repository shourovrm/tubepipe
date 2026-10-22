package org.tubepipe.tv

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import org.tubepipe.core.database.TubepipeDatabase
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.YoutubeContentRepository
import org.tubepipe.tv.data.FilteringContentRepository
import org.tubepipe.tv.data.SettingsRepository
import org.tubepipe.tv.home.HomeRowsMemory
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.player.MiniPlayerFocus
import org.tubepipe.tv.player.PlaybackController
import org.tubepipe.tv.player.TubepipePlayerEngine

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual dependency wiring (docs/decisions/architecture.md: no Hilt until 40+ bindings). One per process. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val database: TubepipeDatabase = TubepipeDatabase.create(applicationContext)
    val settings: SettingsRepository = SettingsRepository(applicationContext.settingsDataStore)

    // Every screen reads YouTube through the Settings content filters (Shorts, upcoming, members-only).
    val content: ContentRepository = FilteringContentRepository(YoutubeContentRepository(), settings.settings)

    /** The one running player; it outlives screens so the video continues in the corner window. */
    val playback = PlaybackController(createEngine = { TubepipePlayerEngine(applicationContext) })

    /**
     * The screen stack lives with the process, not the activity: Android destroys and recreates the activity
     * while the app is in the background (the Mi Box has 2 GB), and a stack owned by it would reset to Home.
     */
    val navigator = AppNavigator()

    /** Focus hand-over between the shell's Back key and the corner window. */
    val miniPlayerFocus = MiniPlayerFocus()

    /** Last rows Home showed, so a recreated Home screen is not reduced to one saved row while it reloads. */
    val homeRowsMemory = HomeRowsMemory()
}
