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

// Settings ported from PipePipe (player defaults, history and search, content filters, SponsorBlock).
@OptIn(ExperimentalCoroutinesApi::class)
class PortedSettingsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun repository(scope: TestScope) = SettingsRepository(
        PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { temporaryFolder.newFile("settings.preferences_pb").also { it.delete() } },
        ),
        deviceLocale = { Locale.US },
    )

    @Test
    fun defaultsMatchPipePipe() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository(this).settings.first()

        assertEquals(VideoQuality.Auto, settings.defaultQuality)
        assertEquals(VideoCodec.H264, settings.preferredCodec)
        assertEquals("original", settings.preferredAudioLanguage)
        assertEquals(1f, settings.defaultSpeed, 0.001f)
        assertEquals(10, settings.seekStepSeconds)
        assertTrue(settings.autoplayNext)
        assertEquals(false, settings.alwaysStartFromBeginning)
        assertEquals(false, settings.captionsEnabled)
        assertEquals(CaptionSize.Normal, settings.captionSize)
        assertTrue(settings.watchHistoryEnabled)
        assertTrue(settings.resumeEnabled)
        assertTrue(settings.searchHistoryEnabled)
        assertTrue(settings.searchSuggestionsEnabled)
        assertEquals(false, settings.hideShorts)
        assertTrue(settings.hideUpcoming)
        assertEquals(false, settings.hidePaid)
        assertEquals(SponsorBlockCategory.entries.toSet(), settings.sponsorBlockCategories)
        assertTrue(settings.sponsorBlockShowNotice)
    }

    @Test
    fun playerDefaultsAreSaved() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(this)

        repository.setDefaultQuality(VideoQuality.P720)
        repository.setPreferredCodec(VideoCodec.Vp9)
        repository.setPreferredAudioLanguage("bn")
        repository.setDefaultSpeed(1.5f)
        repository.setSeekStepSeconds(30)
        repository.setAutoplayNext(false)
        repository.setAlwaysStartFromBeginning(true)
        repository.setCaptionsEnabled(true)
        repository.setCaptionSize(CaptionSize.Large)

        val settings = repository.settings.first()
        assertEquals(VideoQuality.P720, settings.defaultQuality)
        assertEquals(720, settings.defaultQuality.maxHeight)
        assertEquals(VideoCodec.Vp9, settings.preferredCodec)
        assertEquals("bn", settings.preferredAudioLanguage)
        assertEquals(1.5f, settings.defaultSpeed, 0.001f)
        assertEquals(30, settings.seekStepSeconds)
        assertEquals(false, settings.autoplayNext)
        assertTrue(settings.alwaysStartFromBeginning)
        assertTrue(settings.captionsEnabled)
        assertEquals(CaptionSize.Large, settings.captionSize)
    }

    @Test
    fun seekStepStaysWithinFiveToThirtySeconds() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(this)
        repository.setSeekStepSeconds(2)
        assertEquals(5, repository.settings.first().seekStepSeconds)
        repository.setSeekStepSeconds(90)
        assertEquals(30, repository.settings.first().seekStepSeconds)
    }

    @Test
    fun historySearchAndFilterSwitchesAreSaved() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(this)

        repository.setWatchHistoryEnabled(false)
        repository.setResumeEnabled(false)
        repository.setSearchHistoryEnabled(false)
        repository.setSearchSuggestionsEnabled(false)
        repository.setHideShorts(true)
        repository.setHideUpcoming(false)
        repository.setHidePaid(true)

        val settings = repository.settings.first()
        assertEquals(false, settings.watchHistoryEnabled)
        assertEquals(false, settings.resumeEnabled)
        assertEquals(false, settings.searchHistoryEnabled)
        assertEquals(false, settings.searchSuggestionsEnabled)
        assertTrue(settings.hideShorts)
        assertEquals(false, settings.hideUpcoming)
        assertTrue(settings.hidePaid)
    }

    @Test
    fun sponsorBlockCategoriesToggleOneByOne() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository(this)

        repository.setSponsorBlockCategoryEnabled(SponsorBlockCategory.Filler, enabled = false)
        repository.setSponsorBlockCategoryEnabled(SponsorBlockCategory.Intro, enabled = false)
        repository.setSponsorBlockCategoryEnabled(SponsorBlockCategory.Intro, enabled = true)
        repository.setSponsorBlockShowNotice(false)

        val settings = repository.settings.first()
        assertEquals(
            SponsorBlockCategory.entries.toSet() - SponsorBlockCategory.Filler,
            settings.sponsorBlockCategories,
        )
        assertEquals(false, settings.sponsorBlockShowNotice)
    }

    @Test
    fun sponsorBlockCategoriesUseTheApiNames() {
        assertEquals(
            listOf("sponsor", "intro", "outro", "interaction", "selfpromo", "music_offtopic", "preview", "filler"),
            SponsorBlockCategory.entries.map { it.apiName },
        )
    }
}
