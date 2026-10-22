package org.tubepipe.tv.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.tubepipe.tv.R
import org.tubepipe.tv.data.AppSettings
import org.tubepipe.tv.data.CaptionSize
import org.tubepipe.tv.data.SponsorBlockCategory
import org.tubepipe.tv.data.VideoCodec
import org.tubepipe.tv.data.VideoQuality

private val SpeedChoices = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val SeekStepChoicesSeconds = listOf(5, 10, 15, 20, 25, 30)

/** Preferred audio languages: the stored code (an ISO 639-1 code, or "original") and its name. */
private val AudioLanguageChoices = listOf(
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

internal fun LazyListScope.playerPreferenceItems(settings: AppSettings, viewModel: SettingsViewModel) {
    item(key = "player-quality") {
        PickerRow(
            headline = stringResource(R.string.settings_default_quality),
            options = VideoQuality.entries.map { PickerOption(it, stringResource(it.labelResource())) },
            current = settings.defaultQuality,
            onChosen = viewModel::setDefaultQuality,
        )
    }
    item(key = "player-codec") {
        PickerRow(
            headline = stringResource(R.string.settings_preferred_codec),
            options = VideoCodec.entries.map { PickerOption(it, stringResource(it.labelResource())) },
            current = settings.preferredCodec,
            onChosen = viewModel::setPreferredCodec,
        )
    }
    item(key = "player-audio-language") {
        PickerRow(
            headline = stringResource(R.string.settings_audio_language),
            options = AudioLanguageChoices.map { (code, label) -> PickerOption(code, stringResource(label)) },
            current = settings.preferredAudioLanguage,
            onChosen = viewModel::setPreferredAudioLanguage,
        )
    }
    item(key = "player-speed") {
        PickerRow(
            headline = stringResource(R.string.settings_default_speed),
            options = SpeedChoices.map { PickerOption(it, speedLabel(it)) },
            current = settings.defaultSpeed,
            onChosen = viewModel::setDefaultSpeed,
        )
    }
    item(key = "player-seek-step") {
        PickerRow(
            headline = stringResource(R.string.settings_seek_step),
            options = SeekStepChoicesSeconds.map {
                PickerOption(it, stringResource(R.string.settings_seconds_value, it))
            },
            current = settings.seekStepSeconds,
            onChosen = viewModel::setSeekStepSeconds,
        )
    }
    item(key = "player-autoplay") {
        ToggleRow(stringResource(R.string.settings_autoplay_next), settings.autoplayNext, viewModel::setAutoplayNext)
    }
    item(key = "player-from-beginning") {
        ToggleRow(
            stringResource(R.string.settings_always_start_from_beginning),
            settings.alwaysStartFromBeginning,
            viewModel::setAlwaysStartFromBeginning,
        )
    }
    item(key = "player-captions") {
        ToggleRow(
            stringResource(R.string.settings_captions_default),
            settings.captionsEnabled,
            viewModel::setCaptionsEnabled,
        )
    }
    item(key = "player-caption-size") {
        PickerRow(
            headline = stringResource(R.string.settings_caption_size),
            options = CaptionSize.entries.map { PickerOption(it, stringResource(it.labelResource())) },
            current = settings.captionSize,
            onChosen = viewModel::setCaptionSize,
        )
    }
}

internal fun LazyListScope.historyAndSearchItems(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    clearActions: ClearActions,
) {
    item(key = "history-title") { SectionTitle(stringResource(R.string.settings_history_and_search)) }
    item(key = "history-watch") {
        ToggleRow(
            stringResource(R.string.settings_watch_history),
            settings.watchHistoryEnabled,
            viewModel::setWatchHistoryEnabled,
        )
    }
    item(key = "history-resume") {
        ToggleRow(stringResource(R.string.settings_resume), settings.resumeEnabled, viewModel::setResumeEnabled)
    }
    item(key = "history-search") {
        ToggleRow(
            stringResource(R.string.settings_search_history),
            settings.searchHistoryEnabled,
            viewModel::setSearchHistoryEnabled,
        )
    }
    item(key = "history-suggestions") {
        ToggleRow(
            stringResource(R.string.settings_search_suggestions),
            settings.searchSuggestionsEnabled,
            viewModel::setSearchSuggestionsEnabled,
        )
    }
    item(key = "clear-watch-history") {
        ConfirmActionRow(
            headline = stringResource(R.string.settings_clear_watch_history),
            supporting = stringResource(R.string.settings_clear_watch_history_supporting),
            confirmMessage = stringResource(R.string.settings_clear_watch_history_message),
            confirmLabel = stringResource(R.string.settings_confirm_clear),
            onConfirm = clearActions.clearWatchHistory,
        )
    }
    item(key = "clear-positions") {
        ConfirmActionRow(
            headline = stringResource(R.string.settings_clear_positions),
            supporting = stringResource(R.string.settings_clear_positions_supporting),
            confirmMessage = stringResource(R.string.settings_clear_positions_message),
            confirmLabel = stringResource(R.string.settings_confirm_clear),
            onConfirm = clearActions.clearPlaybackPositions,
        )
    }
    item(key = "clear-search-history") {
        ConfirmActionRow(
            headline = stringResource(R.string.settings_clear_search_history),
            supporting = stringResource(R.string.settings_clear_search_history_supporting),
            confirmMessage = stringResource(R.string.settings_clear_search_history_message),
            confirmLabel = stringResource(R.string.settings_confirm_clear),
            onConfirm = clearActions.clearSearchHistory,
        )
    }
}

internal fun LazyListScope.contentFilterItems(settings: AppSettings, viewModel: SettingsViewModel) {
    item(key = "filters-title") { SectionTitle(stringResource(R.string.settings_content_filters)) }
    item(key = "filters-shorts") {
        ToggleRow(stringResource(R.string.settings_hide_shorts), settings.hideShorts, viewModel::setHideShorts)
    }
    item(key = "filters-upcoming") {
        ToggleRow(stringResource(R.string.settings_hide_upcoming), settings.hideUpcoming, viewModel::setHideUpcoming)
    }
    item(key = "filters-paid") {
        ToggleRow(stringResource(R.string.settings_hide_paid), settings.hidePaid, viewModel::setHidePaid)
    }
}

internal fun LazyListScope.sponsorBlockItems(settings: AppSettings, viewModel: SettingsViewModel) {
    item(key = "sponsorblock-title") { SectionTitle(stringResource(R.string.settings_sponsorblock)) }
    item(key = "sponsorblock") {
        SettingRow(
            headline = stringResource(R.string.settings_sponsorblock),
            supporting = stringResource(R.string.settings_sponsorblock_supporting),
            trailing = stringResource(
                if (settings.sponsorBlockEnabled) R.string.settings_on else R.string.settings_off,
            ),
            onClick = viewModel::toggleSponsorBlock,
        )
    }
    SponsorBlockCategory.entries.forEach { category ->
        item(key = "sponsorblock-${category.name}") {
            ToggleRow(
                headline = stringResource(category.labelResource()),
                checked = category in settings.sponsorBlockCategories,
                onCheckedChange = { enabled -> viewModel.setSponsorBlockCategoryEnabled(category, enabled) },
            )
        }
    }
    item(key = "sponsorblock-notice") {
        ToggleRow(
            stringResource(R.string.settings_sponsorblock_show_notice),
            settings.sponsorBlockShowNotice,
            viewModel::setSponsorBlockShowNotice,
        )
    }
}

@Composable
private fun speedLabel(speed: Float): String {
    // 1.0 reads as "1x"; 1.25 keeps its decimals.
    val number = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return stringResource(R.string.settings_speed_value, number)
}

@StringRes
private fun VideoQuality.labelResource(): Int = when (this) {
    VideoQuality.Auto -> R.string.settings_quality_auto
    VideoQuality.P1080 -> R.string.settings_quality_1080
    VideoQuality.P720 -> R.string.settings_quality_720
    VideoQuality.P480 -> R.string.settings_quality_480
    VideoQuality.P360 -> R.string.settings_quality_360
}

@StringRes
private fun VideoCodec.labelResource(): Int = when (this) {
    VideoCodec.H264 -> R.string.settings_codec_h264
    VideoCodec.Vp9 -> R.string.settings_codec_vp9
    VideoCodec.Av1 -> R.string.settings_codec_av1
}

@StringRes
private fun CaptionSize.labelResource(): Int = when (this) {
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
