package org.tubepipe.tv

import android.app.Application
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.tubepipe.core.service.ExtractorInitializer

class TubepipeApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // The first screen queries the extractor at once, so the country must be set before any
        // screen starts. Reading one small preferences file costs a few milliseconds.
        val initialCountry = runBlocking { container.settings.settings.first().contentCountry }
        ExtractorInitializer.initialize(languageCode = Locale.getDefault().language, countryCode = initialCountry)

        applicationScope.launch {
            container.settings.settings
                .map { it.contentCountry }
                .distinctUntilChanged()
                .collect { country -> ExtractorInitializer.setContentCountry(country) }
        }
        applicationScope.launch {
            container.settings.settings
                .map { it.hideSensitiveContent }
                .distinctUntilChanged()
                .collect { enabled -> ExtractorInitializer.setRestrictedMode(enabled) }
        }
    }
}
