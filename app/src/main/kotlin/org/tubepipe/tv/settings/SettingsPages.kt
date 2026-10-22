package org.tubepipe.tv.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.R
import org.tubepipe.tv.data.AppSettings
import org.tubepipe.tv.data.CaptionSize
import org.tubepipe.tv.data.HomeSection
import org.tubepipe.tv.data.SponsorBlockCategory
import org.tubepipe.tv.data.VideoCodec
import org.tubepipe.tv.data.VideoQuality

private val SpeedChoices = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val SeekStepChoicesSeconds = listOf(5, 10, 15, 20, 25, 30)
private const val SPONSOR_BLOCK_GRID_COLUMNS = 4
private val ControlsColumnWidth = 560.dp
private const val PLAYER_CLIENT_VISIONOS = "visionos"
private const val PLAYER_CLIENT_MWEB = "mweb"

/** Preferred audio languages: the stored code (an ISO 639-1 code, or "original") and its name. */
internal val AudioLanguageChoices = listOf(
    AppSettings.ORIGINAL_AUDIO_LANGUAGE to R.string.settings_audio_language_original,
    "bn" to R.string.settings_language_bangla,
    "en" to R.string.settings_language_english,
    "hi" to R.string.settings_language_hindi,
    "ar" to R.string.settings_language_arabic,
    "es" to R.string.settings_language_spanish,
    "fr" to R.string.settings_language_french,
    "de" to R.string.settings_language_german,
    "id" to R.string.settings_language_indonesian,
    "ja" to R.string.settings_language_japanese,
    "pt" to R.string.settings_language_portuguese,
    "ru" to R.string.settings_language_russian,
    "tr" to R.string.settings_language_turkish,
)

/** What the three "clear" rows do; the screen supplies them because they need the database. */
internal class ClearActions(
    val clearWatchHistory: () -> Unit,
    val clearPlaybackPositions: () -> Unit,
    val clearSearchHistory: () -> Unit,
)

/**
 * The frame every sub-page shares: a "Settings ›" breadcrumb, the page [title] and the rows in one column.
 * Focus moves to the first row when the page opens.
 */
@Composable
internal fun SettingsPageScaffold(title: String, controls: @Composable ColumnScope.() -> Unit) {
    val firstControlFocus = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TubepipeSpacing.SafeMarginHorizontal, vertical = TubepipeSpacing.SafeMarginVertical),
    ) {
        Text(
            text = stringResource(R.string.settings_breadcrumb),
            style = MaterialTheme.typography.labelLarge,
            color = TubepipeColors.OnSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TubepipeColors.OnSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .width(ControlsColumnWidth)
                .focusRequester(firstControlFocus)
                .focusGroup()
                .verticalScroll(rememberScrollState()),
            content = controls,
        )
    }
    LaunchedEffect(Unit) {
        // The controls attach on the first frame; focusing earlier finds nothing to focus.
        withFrameNanos { }
        firstControlFocus.requestFocus()
    }
}

@Composable
internal fun PlaybackPage(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsPageScaffold(stringResource(R.string.settings_page_playback)) {
        val speedName = stringResource(R.string.settings_default_speed)
        SettingsSliderRow(
            name = speedName,
            choices = SpeedChoices,
            current = settings.defaultSpeed,
            valueLabel = speedLabel(settings.defaultSpeed),
            onChosen = viewModel::setDefaultSpeed,
            icon = Icons.Filled.PlayArrow,
        )
        val seekStepName = stringResource(R.string.settings_seek_step)
        SettingsSliderRow(
            name = seekStepName,
            choices = SeekStepChoicesSeconds,
            current = settings.seekStepSeconds,
            valueLabel = stringResource(R.string.settings_seconds_value, settings.seekStepSeconds),
            onChosen = viewModel::setSeekStepSeconds,
        )
        val autoplayName = stringResource(R.string.settings_autoplay_next)
        SettingsToggleRow(
            name = autoplayName,
            checked = settings.autoplayNext,
            onCheckedChange = viewModel::setAutoplayNext,
        )
        val resumeName = stringResource(R.string.settings_resume)
        SettingsToggleRow(
            name = resumeName,
            checked = settings.resumeEnabled,
            onCheckedChange = viewModel::setResumeEnabled,
            icon = Icons.Filled.Refresh,
        )
        val fromBeginningName = stringResource(R.string.settings_always_start_from_beginning)
        SettingsToggleRow(
            name = fromBeginningName,
            checked = settings.alwaysStartFromBeginning,
            onCheckedChange = viewModel::setAlwaysStartFromBeginning,
        )
    }
}

@Composable
internal fun QualityAndAudioPage(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsPageScaffold(stringResource(R.string.settings_page_quality)) {
        val qualityName = stringResource(R.string.settings_default_quality)
        SettingsSegmentedRow(
            name = qualityName,
            options = VideoQuality.entries.map { PickerOption(it, stringResource(it.labelResource())) },
            current = settings.defaultQuality,
            onChosen = viewModel::setDefaultQuality,
        )
        val codecName = stringResource(R.string.settings_preferred_codec)
        SettingsSegmentedRow(
            name = codecName,
            options = VideoCodec.entries.map { PickerOption(it, stringResource(it.labelResource())) },
            current = settings.preferredCodec,
            onChosen = viewModel::setPreferredCodec,
        )
        val audioLanguageName = stringResource(R.string.settings_audio_language)
        SettingsDropdownRow(
            name = audioLanguageName,
            options = AudioLanguageChoices.map { (code, label) -> PickerOption(code, stringResource(label)) },
            current = settings.preferredAudioLanguage,
            onChosen = viewModel::setPreferredAudioLanguage,
        )
    }
}

@Composable
internal fun CaptionsPage(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsPageScaffold(stringResource(R.string.settings_page_captions)) {
        val captionsName = stringResource(R.string.settings_captions_default)
        SettingsToggleRow(
            name = captionsName,
            checked = settings.captionsEnabled,
            onCheckedChange = viewModel::setCaptionsEnabled,
        )
        val sizeName = stringResource(R.string.settings_caption_size)
        val sample = stringResource(R.string.settings_caption_sample)
        SettingsSegmentedRow(
            name = sizeName,
            options = CaptionSize.entries.map { PickerOption(it, stringResource(it.labelResource())) },
            current = settings.captionSize,
            onChosen = viewModel::setCaptionSize,
            chipText = { sample },
            chipFontSize = { option -> option.value.sampleFontSize() },
        )
    }
}

@Composable
internal fun SponsorBlockPage(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsPageScaffold(stringResource(R.string.settings_sponsorblock)) {
        val masterName = stringResource(R.string.settings_sponsorblock_master)
        SettingsToggleRow(
            name = masterName,
            checked = settings.sponsorBlockEnabled,
            onCheckedChange = { viewModel.toggleSponsorBlock() },
        )
        SettingsGroupLabel(stringResource(R.string.settings_sponsorblock_categories))
        SponsorBlockCategory.entries.chunked(SPONSOR_BLOCK_GRID_COLUMNS).forEach { rowCategories ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                rowCategories.forEach { category ->
                    val categoryName = stringResource(category.labelResource())
                    SettingsToggleChip(
                        name = categoryName,
                        dotColor = category.seekBarColor(),
                        checked = category in settings.sponsorBlockCategories,
                        onCheckedChange = { enabled -> viewModel.setSponsorBlockCategoryEnabled(category, enabled) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        val noticeName = stringResource(R.string.settings_sponsorblock_show_notice)
        SettingsToggleRow(
            name = noticeName,
            checked = settings.sponsorBlockShowNotice,
            onCheckedChange = viewModel::setSponsorBlockShowNotice,
            icon = Icons.Filled.Info,
        )
    }
}

@Composable
internal fun HomeScreenPage(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsPageScaffold(stringResource(R.string.settings_page_home)) {
        settings.homeSectionOrder.forEach { section ->
            // Keyed by section so a moved row keeps its focus instead of handing it to its new neighbour.
            key(section) {
                val sectionName = stringResource(section.labelResource())
                HomeSectionRow(
                    name = sectionName,
                    hidden = section in settings.hiddenHomeSections,
                    onToggleHidden = { viewModel.toggleSectionHidden(section) },
                    onMoveUp = { viewModel.moveSectionUp(section) },
                    onMoveDown = { viewModel.moveSectionDown(section) },
                )
            }
        }
    }
}

@Composable
internal fun ParentalPage(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsPageScaffold(stringResource(R.string.settings_page_parental)) {
        SettingsToggleRow(
            name = stringResource(R.string.settings_hide_sensitive),
            checked = settings.hideSensitiveContent,
            onCheckedChange = viewModel::setHideSensitiveContent,
            icon = Icons.Filled.Lock,
        )
        SettingsToggleRow(
            name = stringResource(R.string.settings_hide_adult),
            checked = settings.hideAdultVideos,
            onCheckedChange = viewModel::setHideAdultVideos,
        )
        SettingsToggleRow(
            name = stringResource(R.string.settings_hide_shorts),
            checked = settings.hideShorts,
            onCheckedChange = viewModel::setHideShorts,
        )
        SettingsToggleRow(
            name = stringResource(R.string.settings_hide_upcoming),
            checked = settings.hideUpcoming,
            onCheckedChange = viewModel::setHideUpcoming,
        )
        SettingsToggleRow(
            name = stringResource(R.string.settings_hide_paid),
            checked = settings.hidePaid,
            onCheckedChange = viewModel::setHidePaid,
        )
    }
}

@Composable
internal fun HistoryAndPrivacyPage(settings: AppSettings, viewModel: SettingsViewModel, clearActions: ClearActions) {
    SettingsPageScaffold(stringResource(R.string.settings_page_history)) {
        val watchHistoryName = stringResource(R.string.settings_watch_history)
        SettingsToggleRow(
            name = watchHistoryName,
            checked = settings.watchHistoryEnabled,
            onCheckedChange = viewModel::setWatchHistoryEnabled,
        )
        val searchHistoryName = stringResource(R.string.settings_search_history)
        SettingsToggleRow(
            name = searchHistoryName,
            checked = settings.searchHistoryEnabled,
            onCheckedChange = viewModel::setSearchHistoryEnabled,
        )
        val suggestionsName = stringResource(R.string.settings_search_suggestions)
        SettingsToggleRow(
            name = suggestionsName,
            checked = settings.searchSuggestionsEnabled,
            onCheckedChange = viewModel::setSearchSuggestionsEnabled,
        )
        val clearLabel = stringResource(R.string.settings_confirm_clear)
        val clearWatchName = stringResource(R.string.settings_clear_watch_history)
        SettingsConfirmActionRow(
            name = clearWatchName,
            confirmMessage = stringResource(R.string.settings_clear_watch_history_message),
            confirmLabel = clearLabel,
            onConfirm = clearActions.clearWatchHistory,
        )
        val clearPositionsName = stringResource(R.string.settings_clear_positions)
        SettingsConfirmActionRow(
            name = clearPositionsName,
            confirmMessage = stringResource(R.string.settings_clear_positions_message),
            confirmLabel = clearLabel,
            onConfirm = clearActions.clearPlaybackPositions,
        )
        val clearSearchName = stringResource(R.string.settings_clear_search_history)
        SettingsConfirmActionRow(
            name = clearSearchName,
            confirmMessage = stringResource(R.string.settings_clear_search_history_message),
            confirmLabel = clearLabel,
            onConfirm = clearActions.clearSearchHistory,
        )
    }
}

@Composable
internal fun RegionAndEnginePage(settings: AppSettings, countries: List<CountryOption>, viewModel: SettingsViewModel) {
    SettingsPageScaffold(stringResource(R.string.settings_page_region)) {
        val countryName = stringResource(R.string.settings_content_country)
        SettingsDropdownRow(
            name = countryName,
            options = countries.map { PickerOption(it.code, it.name) },
            current = settings.contentCountry,
            onChosen = viewModel::setContentCountry,
            isSearchable = true,
        )
        val engineName = stringResource(R.string.settings_player_engine)
        SettingsSegmentedRow(
            name = engineName,
            options = listOf(
                PickerOption(PLAYER_CLIENT_VISIONOS, stringResource(R.string.settings_engine_standard)),
                PickerOption(PLAYER_CLIENT_MWEB, stringResource(R.string.settings_engine_fallback)),
            ),
            current = settings.playerClientName,
            onChosen = viewModel::setPlayerClient,
        )
    }
}

@Composable
internal fun AboutPage(versionName: String) {
    SettingsPageScaffold(stringResource(R.string.settings_about)) {
        SettingsInfoRow(name = stringResource(R.string.settings_about_version_name), value = versionName)
        SettingsInfoRow(
            name = stringResource(R.string.settings_about_license_name),
            value = stringResource(R.string.settings_about_license_value),
        )
        SettingsGroupLabel(stringResource(R.string.settings_about_credits))
        SettingsInfoRow(name = stringResource(R.string.settings_about_newpipe))
        SettingsInfoRow(name = stringResource(R.string.settings_about_pipepipe))
        // GPL-3.0: the About page must say the source is public (AGENTS.md, non-negotiables).
        SettingsInfoRow(
            name = stringResource(R.string.settings_about_source_name),
            value = stringResource(R.string.settings_about_source_value),
        )
    }
}

/** One home section: its name, a Hidden marker, and three chips to show or hide it and to move it. */
@Composable
private fun HomeSectionRow(
    name: String,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    SettingsChipsRow(
        name = if (hidden) "$name · ${stringResource(R.string.settings_hidden_marker)}" else name,
        dimmed = hidden,
    ) {
        ChipButton(
            text = stringResource(if (hidden) R.string.settings_show else R.string.settings_hide),
            onClick = onToggleHidden,
        )
        ChipButton(text = stringResource(R.string.settings_move_up), onClick = onMoveUp)
        ChipButton(text = stringResource(R.string.settings_move_down), onClick = onMoveDown)
    }
}

@Composable
internal fun speedLabel(speed: Float): String {
    // 1.0 reads as "1×"; 1.25 keeps its decimals.
    val number = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return stringResource(R.string.settings_speed_value, number)
}

private fun CaptionSize.sampleFontSize() = when (this) {
    CaptionSize.Small -> 12.sp
    CaptionSize.Normal -> 16.sp
    CaptionSize.Large -> 20.sp
}

/** Seek-bar colours of the SponsorBlock categories, as PipePipe draws them. */
private fun SponsorBlockCategory.seekBarColor(): Color = when (this) {
    SponsorBlockCategory.Sponsor -> Color(0xFF00D400)
    SponsorBlockCategory.Intro -> Color(0xFF00FFFF)
    SponsorBlockCategory.Outro -> Color(0xFF0202ED)
    SponsorBlockCategory.Interaction -> Color(0xFFCC00FF)
    SponsorBlockCategory.SelfPromo -> Color(0xFFFFFF00)
    SponsorBlockCategory.NonMusic -> Color(0xFFFF9900)
    SponsorBlockCategory.Preview -> Color(0xFF008FD6)
    SponsorBlockCategory.Filler -> Color(0xFF7300FF)
}

private fun HomeSection.labelResource(): Int = when (this) {
    HomeSection.ContinueWatching -> R.string.settings_section_continue_watching
    HomeSection.Recommended -> R.string.settings_section_recommended
    HomeSection.Mix -> R.string.settings_section_mix
    HomeSection.NewFromSubscriptions -> R.string.settings_section_new_from_subscriptions
    HomeSection.TrendingMusic -> R.string.settings_section_trending_music
    HomeSection.TrendingMovies -> R.string.settings_section_trending_movies
}

@StringRes
internal fun VideoQuality.labelResource(): Int = when (this) {
    VideoQuality.Auto -> R.string.settings_quality_auto
    VideoQuality.P1080 -> R.string.settings_quality_1080
    VideoQuality.P720 -> R.string.settings_quality_720
    VideoQuality.P480 -> R.string.settings_quality_480
    VideoQuality.P360 -> R.string.settings_quality_360
}

@StringRes
internal fun VideoCodec.labelResource(): Int = when (this) {
    VideoCodec.H264 -> R.string.settings_codec_h264
    VideoCodec.Vp9 -> R.string.settings_codec_vp9
    VideoCodec.Av1 -> R.string.settings_codec_av1
}

@StringRes
internal fun CaptionSize.labelResource(): Int = when (this) {
    CaptionSize.Small -> R.string.settings_caption_small
    CaptionSize.Normal -> R.string.settings_caption_normal
    CaptionSize.Large -> R.string.settings_caption_large
}

@StringRes
private fun SponsorBlockCategory.labelResource(): Int = when (this) {
    SponsorBlockCategory.Sponsor -> R.string.settings_category_sponsor
    SponsorBlockCategory.Intro -> R.string.settings_category_intro
    SponsorBlockCategory.Outro -> R.string.settings_category_outro
    SponsorBlockCategory.Interaction -> R.string.settings_category_interaction
    SponsorBlockCategory.SelfPromo -> R.string.settings_category_self_promo
    SponsorBlockCategory.NonMusic -> R.string.settings_category_non_music
    SponsorBlockCategory.Preview -> R.string.settings_category_preview
    SponsorBlockCategory.Filler -> R.string.settings_category_filler
}

@StringRes
internal fun String.engineLabelResource(): Int =
    if (this == PLAYER_CLIENT_MWEB) R.string.settings_engine_fallback else R.string.settings_engine_standard
