package org.tubepipe.tv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

/** Highest quality to pick by default; Auto lets the player adapt up to the screen size. */
enum class VideoQuality(val maxHeight: Int?) {
    Auto(null),
    P1080(1080),
    P720(720),
    P480(480),
    P360(360),
}

enum class VideoCodec { H264, Vp9, Av1 }

enum class CaptionSize { Small, Normal, Large }

/** SponsorBlock skip categories, with the names its API expects (https://wiki.sponsor.ajay.app/w/Types). */
enum class SponsorBlockCategory(val apiName: String) {
    Sponsor("sponsor"),
    Intro("intro"),
    Outro("outro"),
    Interaction("interaction"),
    SelfPromo("selfpromo"),
    NonMusic("music_offtopic"),
    Preview("preview"),
    Filler("filler"),
}

/** Defaults follow PipePipe's settings_keys.xml so its users find the same behaviour. */
data class AppSettings(
    val contentCountry: String,
    val playerClientName: String,
    val homeSectionOrder: List<HomeSection>,
    val hiddenHomeSections: Set<HomeSection>,
    val sponsorBlockEnabled: Boolean,
    val defaultQuality: VideoQuality = VideoQuality.Auto,
    val preferredCodec: VideoCodec = VideoCodec.H264,
    val preferredAudioLanguage: String = ORIGINAL_AUDIO_LANGUAGE,
    val defaultSpeed: Float = 1f,
    val seekStepSeconds: Int = DEFAULT_SEEK_STEP_SECONDS,
    val autoplayNext: Boolean = true,
    val alwaysStartFromBeginning: Boolean = false,
    val captionsEnabled: Boolean = false,
    val captionSize: CaptionSize = CaptionSize.Normal,
    val watchHistoryEnabled: Boolean = true,
    val resumeEnabled: Boolean = true,
    val searchHistoryEnabled: Boolean = true,
    val searchSuggestionsEnabled: Boolean = true,
    val hideShorts: Boolean = false,
    val hideUpcoming: Boolean = true,
    val hidePaid: Boolean = false,
    val sponsorBlockCategories: Set<SponsorBlockCategory> = SponsorBlockCategory.entries.toSet(),
    val sponsorBlockShowNotice: Boolean = true,
) {
    companion object {
        const val ORIGINAL_AUDIO_LANGUAGE = "original"
        const val DEFAULT_SEEK_STEP_SECONDS = 10
        val SEEK_STEP_RANGE_SECONDS = 5..30
    }

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
        val defaultQuality = stringPreferencesKey("default_quality")
        val preferredCodec = stringPreferencesKey("preferred_codec")
        val preferredAudioLanguage = stringPreferencesKey("preferred_audio_language")
        val defaultSpeed = floatPreferencesKey("default_speed")
        val seekStepSeconds = intPreferencesKey("seek_step_seconds")
        val autoplayNext = booleanPreferencesKey("autoplay_next")
        val alwaysStartFromBeginning = booleanPreferencesKey("always_start_from_beginning")
        val captionsEnabled = booleanPreferencesKey("captions_enabled")
        val captionSize = stringPreferencesKey("caption_size")
        val watchHistoryEnabled = booleanPreferencesKey("watch_history_enabled")
        val resumeEnabled = booleanPreferencesKey("resume_enabled")
        val searchHistoryEnabled = booleanPreferencesKey("search_history_enabled")
        val searchSuggestionsEnabled = booleanPreferencesKey("search_suggestions_enabled")
        val hideShorts = booleanPreferencesKey("hide_shorts")
        val hideUpcoming = booleanPreferencesKey("hide_upcoming")
        val hidePaid = booleanPreferencesKey("hide_paid")
        val disabledSponsorBlockCategories = stringSetPreferencesKey("disabled_sponsor_block_categories")
        val sponsorBlockShowNotice = booleanPreferencesKey("sponsor_block_show_notice")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            contentCountry = preferences[Keys.contentCountry] ?: countryOfDeviceLocale(),
            playerClientName = preferences[Keys.playerClient] ?: DEFAULT_PLAYER_CLIENT,
            homeSectionOrder = parseSectionOrder(preferences[Keys.homeSectionOrder]),
            hiddenHomeSections = preferences[Keys.hiddenHomeSections].orEmpty().mapNotNull(::sectionOrNull).toSet(),
            sponsorBlockEnabled = preferences[Keys.sponsorBlockEnabled] ?: true,
            defaultQuality = enumOrDefault(preferences[Keys.defaultQuality], VideoQuality.Auto),
            preferredCodec = enumOrDefault(preferences[Keys.preferredCodec], VideoCodec.H264),
            preferredAudioLanguage = preferences[Keys.preferredAudioLanguage] ?: AppSettings.ORIGINAL_AUDIO_LANGUAGE,
            defaultSpeed = preferences[Keys.defaultSpeed] ?: 1f,
            seekStepSeconds = preferences[Keys.seekStepSeconds] ?: AppSettings.DEFAULT_SEEK_STEP_SECONDS,
            autoplayNext = preferences[Keys.autoplayNext] ?: true,
            alwaysStartFromBeginning = preferences[Keys.alwaysStartFromBeginning] ?: false,
            captionsEnabled = preferences[Keys.captionsEnabled] ?: false,
            captionSize = enumOrDefault(preferences[Keys.captionSize], CaptionSize.Normal),
            watchHistoryEnabled = preferences[Keys.watchHistoryEnabled] ?: true,
            resumeEnabled = preferences[Keys.resumeEnabled] ?: true,
            searchHistoryEnabled = preferences[Keys.searchHistoryEnabled] ?: true,
            searchSuggestionsEnabled = preferences[Keys.searchSuggestionsEnabled] ?: true,
            hideShorts = preferences[Keys.hideShorts] ?: false,
            hideUpcoming = preferences[Keys.hideUpcoming] ?: true,
            hidePaid = preferences[Keys.hidePaid] ?: false,
            // Stored as the disabled set, so a category added later starts enabled.
            sponsorBlockCategories = SponsorBlockCategory.entries.toSet() -
                preferences[Keys.disabledSponsorBlockCategories].orEmpty().mapNotNull { name ->
                    SponsorBlockCategory.entries.firstOrNull { it.name == name }
                }.toSet(),
            sponsorBlockShowNotice = preferences[Keys.sponsorBlockShowNotice] ?: true,
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

    suspend fun setDefaultQuality(quality: VideoQuality) = save(Keys.defaultQuality, quality.name)

    suspend fun setPreferredCodec(codec: VideoCodec) = save(Keys.preferredCodec, codec.name)

    suspend fun setPreferredAudioLanguage(languageCode: String) = save(Keys.preferredAudioLanguage, languageCode)

    suspend fun setDefaultSpeed(speed: Float) = save(Keys.defaultSpeed, speed)

    suspend fun setSeekStepSeconds(seconds: Int) = save(
        Keys.seekStepSeconds,
        seconds.coerceIn(AppSettings.SEEK_STEP_RANGE_SECONDS.first, AppSettings.SEEK_STEP_RANGE_SECONDS.last),
    )

    suspend fun setAutoplayNext(enabled: Boolean) = save(Keys.autoplayNext, enabled)

    suspend fun setAlwaysStartFromBeginning(enabled: Boolean) = save(Keys.alwaysStartFromBeginning, enabled)

    suspend fun setCaptionsEnabled(enabled: Boolean) = save(Keys.captionsEnabled, enabled)

    suspend fun setCaptionSize(size: CaptionSize) = save(Keys.captionSize, size.name)

    suspend fun setWatchHistoryEnabled(enabled: Boolean) = save(Keys.watchHistoryEnabled, enabled)

    suspend fun setResumeEnabled(enabled: Boolean) = save(Keys.resumeEnabled, enabled)

    suspend fun setSearchHistoryEnabled(enabled: Boolean) = save(Keys.searchHistoryEnabled, enabled)

    suspend fun setSearchSuggestionsEnabled(enabled: Boolean) = save(Keys.searchSuggestionsEnabled, enabled)

    suspend fun setHideShorts(enabled: Boolean) = save(Keys.hideShorts, enabled)

    suspend fun setHideUpcoming(enabled: Boolean) = save(Keys.hideUpcoming, enabled)

    suspend fun setHidePaid(enabled: Boolean) = save(Keys.hidePaid, enabled)

    suspend fun setSponsorBlockShowNotice(enabled: Boolean) = save(Keys.sponsorBlockShowNotice, enabled)

    suspend fun setSponsorBlockCategoryEnabled(category: SponsorBlockCategory, enabled: Boolean) {
        dataStore.edit { preferences ->
            val disabled = preferences[Keys.disabledSponsorBlockCategories].orEmpty()
            preferences[Keys.disabledSponsorBlockCategories] =
                if (enabled) disabled - category.name else disabled + category.name
        }
    }

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

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
