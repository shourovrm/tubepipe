package org.tubepipe.tv.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tubepipe.tv.data.HomeSection
import org.tubepipe.tv.data.SettingsRepository
import org.tubepipe.tv.data.SponsorBlockCategory
import org.tubepipe.tv.data.VideoQuality
import org.tubepipe.tv.testing.MainDispatcherRule

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun repository(scope: kotlinx.coroutines.CoroutineScope) = SettingsRepository(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { temporaryFolder.newFile("settings.preferences_pb").also { it.delete() } },
        ),
        deviceLocale = { Locale.US },
    )

    @Test
    fun countryListHasNamesSortedAlphabetically() = runTest(timeout = 30.seconds) {
        val viewModel = SettingsViewModel(repository(backgroundScope), displayLocale = Locale.ENGLISH)

        val countries = viewModel.state.first { it.settings != null }.countries

        assertTrue(countries.any { it.code == "BD" && it.name == "Bangladesh" })
        assertEquals(countries.sortedBy { it.name }, countries)
    }

    @Test
    fun choosingACountryAndPlayerClientIsSaved() = runTest(timeout = 30.seconds) {
        val viewModel = SettingsViewModel(repository(backgroundScope), displayLocale = Locale.ENGLISH)
        viewModel.state.first { it.settings != null }

        viewModel.setContentCountry("BD")
        viewModel.setPlayerClient("mweb")

        val settings = viewModel.state.first {
            it.settings?.contentCountry == "BD" &&
                it.settings.playerClientName == "mweb"
        }
        assertEquals("BD", settings.settings?.contentCountry)
    }

    @Test
    fun movingSectionsStopsAtTheEnds() = runTest(timeout = 30.seconds) {
        val viewModel = SettingsViewModel(repository(backgroundScope), displayLocale = Locale.ENGLISH)
        viewModel.state.first { it.settings != null }

        viewModel.moveSectionUp(HomeSection.ContinueWatching)
        viewModel.moveSectionDown(HomeSection.TrendingMovies)
        viewModel.moveSectionDown(HomeSection.ContinueWatching)

        val order = viewModel.state.first { it.settings?.homeSectionOrder?.first() == HomeSection.Recommended }
            .settings!!.homeSectionOrder
        assertEquals(
            listOf(
                HomeSection.Recommended,
                HomeSection.ContinueWatching,
                HomeSection.Mix,
                HomeSection.NewFromSubscriptions,
                HomeSection.TrendingMusic,
                HomeSection.TrendingMovies,
            ),
            order,
        )
    }

    @Test
    fun hidingASectionAndSponsorBlockToggle() = runTest(timeout = 30.seconds) {
        val viewModel = SettingsViewModel(repository(backgroundScope), displayLocale = Locale.ENGLISH)
        viewModel.state.first { it.settings != null }

        viewModel.toggleSectionHidden(HomeSection.Mix)
        viewModel.toggleSponsorBlock()

        val settings = viewModel.state.first { it.settings?.hiddenHomeSections == setOf(HomeSection.Mix) }.settings!!
        assertEquals(
            false,
            viewModel.state.first {
                it.settings?.sponsorBlockEnabled == false
            }.settings?.sponsorBlockEnabled,
        )
        assertEquals(setOf(HomeSection.Mix), settings.hiddenHomeSections)

        viewModel.toggleSectionHidden(HomeSection.Mix)
        viewModel.state.first { it.settings?.hiddenHomeSections?.isEmpty() == true }
    }

    @Test
    fun portedSettingsArePassedThrough() = runTest(timeout = 30.seconds) {
        val viewModel = SettingsViewModel(repository(backgroundScope), displayLocale = Locale.ENGLISH)
        viewModel.state.first { it.settings != null }

        viewModel.setDefaultQuality(VideoQuality.P720)
        viewModel.setSeekStepSeconds(15)
        viewModel.setHideShorts(true)
        viewModel.setSponsorBlockCategoryEnabled(SponsorBlockCategory.Filler, enabled = false)

        val settings = viewModel.state.first {
            it.settings?.seekStepSeconds == 15 &&
                it.settings.hideShorts &&
                SponsorBlockCategory.Filler !in it.settings.sponsorBlockCategories
        }.settings!!
        assertEquals(VideoQuality.P720, settings.defaultQuality)
    }
}
