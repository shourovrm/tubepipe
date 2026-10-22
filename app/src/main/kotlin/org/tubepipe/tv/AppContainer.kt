package org.tubepipe.tv

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import org.tubepipe.core.database.TubepipeDatabase
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.YoutubeContentRepository
import org.tubepipe.tv.data.SettingsRepository

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual dependency wiring (docs/decisions/architecture.md: no Hilt until 40+ bindings). One per process. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val content: ContentRepository = YoutubeContentRepository()
    val database: TubepipeDatabase = TubepipeDatabase.create(applicationContext)
    val settings: SettingsRepository = SettingsRepository(applicationContext.settingsDataStore)
}
