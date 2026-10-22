package org.tubepipe.tv

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import org.tubepipe.core.database.TubepipeDatabase
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.YoutubeContentRepository
import org.tubepipe.tv.data.FilteringContentRepository
import org.tubepipe.tv.data.SettingsRepository

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual dependency wiring (docs/decisions/architecture.md: no Hilt until 40+ bindings). One per process. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val database: TubepipeDatabase = TubepipeDatabase.create(applicationContext)
    val settings: SettingsRepository = SettingsRepository(applicationContext.settingsDataStore)

    // Every screen reads YouTube through the Settings content filters (Shorts, upcoming, members-only).
    val content: ContentRepository = FilteringContentRepository(YoutubeContentRepository(), settings.settings)
}
