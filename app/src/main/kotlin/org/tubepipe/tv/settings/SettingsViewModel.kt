package org.tubepipe.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tubepipe.tv.data.AppSettings
import org.tubepipe.tv.data.CaptionSize
import org.tubepipe.tv.data.HomeSection
import org.tubepipe.tv.data.SettingsRepository
import org.tubepipe.tv.data.SponsorBlockCategory
import org.tubepipe.tv.data.VideoCodec
import org.tubepipe.tv.data.VideoQuality

data class CountryOption(val code: String, val name: String)

data class SettingsState(
    /** Null until the first read from storage finishes. */
    val settings: AppSettings? = null,
    val countries: List<CountryOption> = emptyList(),
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    displayLocale: Locale = Locale.getDefault(),
) : ViewModel() {
    private val countries = buildCountryOptions(displayLocale)

    val state: StateFlow<SettingsState> = settingsRepository.settings
        .map { settings -> SettingsState(settings = settings, countries = countries) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            SettingsState(countries = countries),
        )

    fun setContentCountry(countryCode: String) {
        viewModelScope.launch { settingsRepository.setContentCountry(countryCode) }
    }

    fun setPlayerClient(extractorName: String) {
        viewModelScope.launch { settingsRepository.setPlayerClientName(extractorName) }
    }

    fun moveSectionUp(section: HomeSection) = moveSection(section, offset = -1)

    fun moveSectionDown(section: HomeSection) = moveSection(section, offset = 1)

    fun toggleSectionHidden(section: HomeSection) {
        viewModelScope.launch {
            val currentlyHidden = section in settingsRepository.settings.first().hiddenHomeSections
            settingsRepository.setHomeSectionHidden(section, hidden = !currentlyHidden)
        }
    }

    fun toggleSponsorBlock() {
        viewModelScope.launch {
            val enabled = settingsRepository.settings.first().sponsorBlockEnabled
            settingsRepository.setSponsorBlockEnabled(!enabled)
        }
    }

    fun setDefaultQuality(quality: VideoQuality) {
        viewModelScope.launch { settingsRepository.setDefaultQuality(quality) }
    }

    fun setPreferredCodec(codec: VideoCodec) {
        viewModelScope.launch { settingsRepository.setPreferredCodec(codec) }
    }

    fun setPreferredAudioLanguage(languageCode: String) {
        viewModelScope.launch { settingsRepository.setPreferredAudioLanguage(languageCode) }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch { settingsRepository.setDefaultSpeed(speed) }
    }

    fun setSeekStepSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setSeekStepSeconds(seconds) }
    }

    fun setAutoplayNext(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoplayNext(enabled) }
    }

    fun setAlwaysStartFromBeginning(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAlwaysStartFromBeginning(enabled) }
    }

    fun setCaptionsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCaptionsEnabled(enabled) }
    }

    fun setCaptionSize(size: CaptionSize) {
        viewModelScope.launch { settingsRepository.setCaptionSize(size) }
    }

    fun setWatchHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWatchHistoryEnabled(enabled) }
    }

    fun setResumeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setResumeEnabled(enabled) }
    }

    fun setSearchHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSearchHistoryEnabled(enabled) }
    }

    fun setSearchSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSearchSuggestionsEnabled(enabled) }
    }

    fun setHideShorts(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHideShorts(enabled) }
    }

    fun setHideUpcoming(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHideUpcoming(enabled) }
    }

    fun setHidePaid(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHidePaid(enabled) }
    }

    fun setHideSensitiveContent(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHideSensitiveContent(enabled) }
    }

    fun setHideAdultVideos(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHideAdultVideos(enabled) }
    }

    fun setSponsorBlockShowNotice(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSponsorBlockShowNotice(enabled) }
    }

    fun setSponsorBlockCategoryEnabled(category: SponsorBlockCategory, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSponsorBlockCategoryEnabled(category, enabled) }
    }

    private fun moveSection(section: HomeSection, offset: Int) {
        viewModelScope.launch {
            // Read the stored order, not the UI state, so two quick presses never act on a stale list.
            val order = settingsRepository.settings.first().homeSectionOrder
            val fromIndex = order.indexOf(section)
            val toIndex = fromIndex + offset
            if (fromIndex < 0 || toIndex !in order.indices) return@launch
            val reordered = order.toMutableList()
            reordered[fromIndex] = order[toIndex]
            reordered[toIndex] = section
            settingsRepository.setHomeSectionOrder(reordered)
        }
    }

    private fun buildCountryOptions(displayLocale: Locale): List<CountryOption> = Locale.getISOCountries()
        .map { code ->
            val region = Locale.Builder().setRegion(code).build()
            CountryOption(code = code, name = region.getDisplayCountry(displayLocale))
        }
        .filter { it.name.isNotBlank() }
        .sortedBy { it.name }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
