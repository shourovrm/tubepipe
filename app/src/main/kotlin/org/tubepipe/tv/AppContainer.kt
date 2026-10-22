package org.tubepipe.tv

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import org.tubepipe.core.database.TubepipeDatabase
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.YoutubeContentRepository
import org.tubepipe.tv.data.FilteringContentRepository
import org.tubepipe.tv.data.SettingsRepository
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
}
