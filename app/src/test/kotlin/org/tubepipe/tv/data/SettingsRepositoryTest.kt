package org.tubepipe.tv.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// UnconfinedTestDispatcher makes DataStore writes visible to the next read without manual scheduling.
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun repository(scope: TestScope, deviceLocale: Locale = Locale.US): SettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { temporaryFolder.newFile("settings.preferences_pb").also { it.delete() } },
        )
        return SettingsRepository(dataStore, deviceLocale = { deviceLocale })
    }

    @Test
    fun contentCountryDefaultsToTheDeviceLocale() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository(this, deviceLocale = Locale.Builder().setLanguage("bn").setRegion("BD").build())
        assertEquals("BD", settings.settings.first().contentCountry)
    }

    @Test
    fun deviceLocaleWithoutACountryFallsBackToTheUnitedStates() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository(this, deviceLocale = Locale.ENGLISH)
        assertEquals("US", settings.settings.first().contentCountry)
    }

    @Test
    fun chosenCountryOverridesTheLocale() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository(this)
        settings.setContentCountry("GB")
        assertEquals("GB", settings.settings.first().contentCountry)
    }

    @Test
    fun playerClientDefaultsToVisionOsAndCanChange() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository(this)
        assertEquals("visionos", settings.settings.first().playerClientName)

        settings.setPlayerClientName("mweb")
        assertEquals("mweb", settings.settings.first().playerClientName)
    }

    @Test
    fun homeSectionsDefaultToTheProductOrderAllVisible() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository(this).settings.first()
        assertEquals(HomeSection.entries, settings.homeSectionOrder)
        assertTrue(settings.hiddenHomeSections.isEmpty())
    }

    @Test
    fun homeSectionsCanBeReorderedAndHidden() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository(this)
        val reordered = listOf(HomeSection.TrendingMusic) + HomeSection.entries.minus(HomeSection.TrendingMusic)

        settings.setHomeSectionOrder(reordered)
        settings.setHomeSectionHidden(HomeSection.Mix, hidden = true)

        val current = settings.settings.first()
        assertEquals(reordered, current.homeSectionOrder)
        assertEquals(setOf(HomeSection.Mix), current.hiddenHomeSections)
        assertEquals(reordered.minus(HomeSection.Mix), current.visibleHomeSections)
    }

    @Test
    fun sectionAddedInALaterVersionIsAppendedToASavedOrder() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository(this)
        settings.setHomeSectionOrder(listOf(HomeSection.TrendingMovies, HomeSection.ContinueWatching))

        val order = settings.settings.first().homeSectionOrder
        assertEquals(listOf(HomeSection.TrendingMovies, HomeSection.ContinueWatching), order.take(2))
        assertEquals(HomeSection.entries.toSet(), order.toSet())
    }

    @Test
    fun sponsorBlockIsOnByDefaultAndCanBeTurnedOff() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository(this)
        assertTrue(settings.settings.first().sponsorBlockEnabled)
        settings.setSponsorBlockEnabled(false)
        assertEquals(false, settings.settings.first().sponsorBlockEnabled)
    }
}
