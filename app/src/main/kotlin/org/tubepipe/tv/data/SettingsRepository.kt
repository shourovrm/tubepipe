package org.tubepipe.tv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Home sections, in the default order from docs/decisions/architecture.md ("Product structure"). */
enum class HomeSection {
    ContinueWatching,
    Recommended,
    Mix,
    NewFromSubscriptions,
    TrendingMusic,
    TrendingMovies,
}

data class AppSettings(
    val contentCountry: String,
    val playerClientName: String,
    val homeSectionOrder: List<HomeSection>,
    val hiddenHomeSections: Set<HomeSection>,
    val sponsorBlockEnabled: Boolean,
) {
    val visibleHomeSections: List<HomeSection> get() = homeSectionOrder.filterNot { it in hiddenHomeSections }
}

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val deviceLocale: () -> Locale = { Locale.getDefault() },
) {
    private object Keys {
        val contentCountry = stringPreferencesKey("content_country")
        val playerClient = stringPreferencesKey("player_client")
        val homeSectionOrder = stringPreferencesKey("home_section_order")
        val hiddenHomeSections = stringSetPreferencesKey("hidden_home_sections")
        val sponsorBlockEnabled = booleanPreferencesKey("sponsor_block_enabled")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            contentCountry = preferences[Keys.contentCountry] ?: countryOfDeviceLocale(),
            playerClientName = preferences[Keys.playerClient] ?: DEFAULT_PLAYER_CLIENT,
            homeSectionOrder = parseSectionOrder(preferences[Keys.homeSectionOrder]),
            hiddenHomeSections = preferences[Keys.hiddenHomeSections].orEmpty().mapNotNull(::sectionOrNull).toSet(),
            sponsorBlockEnabled = preferences[Keys.sponsorBlockEnabled] ?: true,
        )
    }

    suspend fun setContentCountry(countryCode: String) {
        dataStore.edit { it[Keys.contentCountry] = countryCode.uppercase(Locale.ROOT) }
    }

    suspend fun setPlayerClientName(extractorName: String) {
        dataStore.edit { it[Keys.playerClient] = extractorName }
    }

    suspend fun setHomeSectionOrder(order: List<HomeSection>) {
        dataStore.edit { it[Keys.homeSectionOrder] = order.joinToString(separator = ",") { section -> section.name } }
    }

    suspend fun setHomeSectionHidden(section: HomeSection, hidden: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.hiddenHomeSections].orEmpty()
            preferences[Keys.hiddenHomeSections] = if (hidden) current + section.name else current - section.name
        }
    }

    suspend fun setSponsorBlockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.sponsorBlockEnabled] = enabled }
    }

    private fun countryOfDeviceLocale(): String {
        val country = deviceLocale().country
        return country.ifBlank { FALLBACK_COUNTRY }
    }

    /** A saved order keeps its sequence; sections added in later versions go to the end. */
    private fun parseSectionOrder(saved: String?): List<HomeSection> {
        if (saved.isNullOrBlank()) {
            return HomeSection.entries
        }
        val savedSections = saved.split(",").mapNotNull(::sectionOrNull).distinct()
        return savedSections + HomeSection.entries.filterNot { it in savedSections }
    }

    private fun sectionOrNull(name: String): HomeSection? = HomeSection.entries.firstOrNull { it.name == name }

    private companion object {
        const val DEFAULT_PLAYER_CLIENT = "visionos"
        const val FALLBACK_COUNTRY = "US"
    }
}
