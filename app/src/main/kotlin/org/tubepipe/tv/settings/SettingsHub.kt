package org.tubepipe.tv.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.R
import org.tubepipe.tv.data.AppSettings
import org.tubepipe.tv.data.SponsorBlockCategory

/** The nine panels of the settings hub, in the order the tiles appear (left to right, top to bottom). */
internal enum class SettingsCategory(@StringRes val titleResource: Int, val icon: ImageVector) {
    Playback(R.string.settings_page_playback, Icons.Filled.PlayArrow),
    QualityAndAudio(R.string.settings_page_quality, Icons.Filled.Build),
    Captions(R.string.settings_page_captions, Icons.Filled.Edit),
    SponsorBlock(R.string.settings_sponsorblock, Icons.Filled.CheckCircle),
    HomeScreen(R.string.settings_page_home, Icons.Filled.Home),
    ContentFilters(R.string.settings_content_filters, Icons.AutoMirrored.Filled.List),
    HistoryAndPrivacy(R.string.settings_page_history, Icons.Filled.Refresh),
    RegionAndEngine(R.string.settings_page_region, Icons.Filled.LocationOn),
    About(R.string.settings_about, Icons.Filled.Info),
}

private const val TILES_PER_ROW = 3
private val TileHeight = 96.dp
private val TileGap = 12.dp
private const val FOCUSED_TILE_SCALE = 1.03f

/**
 * The hub: a grid of tiles, one per [SettingsCategory], each with a live summary of the values inside.
 * When [restoreFocusTo] is set the tile for that category takes focus, so Back from a page lands where the user left.
 */
@Composable
internal fun SettingsHub(
    settings: AppSettings,
    countryName: String,
    versionName: String,
    restoreFocusTo: SettingsCategory?,
    onOpen: (SettingsCategory) -> Unit,
) {
    val tileFocusRequesters = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    Column(
        verticalArrangement = Arrangement.spacedBy(TileGap),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TubepipeSpacing.SafeMarginHorizontal, vertical = TubepipeSpacing.SafeMarginVertical),
    ) {
        Text(
            text = stringResource(R.string.destination_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = TubepipeColors.OnSurface,
        )
        SettingsCategory.entries.chunked(TILES_PER_ROW).forEach { rowCategories ->
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap), modifier = Modifier.fillMaxWidth()) {
                rowCategories.forEach { category ->
                    SettingsTile(
                        category = category,
                        summary = categorySummary(category, settings, countryName, versionName),
                        onClick = { onOpen(category) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tileFocusRequesters.getValue(category)),
                    )
                }
            }
        }
    }
    if (restoreFocusTo != null) {
        LaunchedEffect(Unit) {
            // The tiles attach on the first frame; focusing earlier finds nothing to focus.
            withFrameNanos { }
            tileFocusRequesters.getValue(restoreFocusTo).requestFocus()
        }
    }
}

@Composable
private fun SettingsTile(category: SettingsCategory, summary: String, onClick: () -> Unit, modifier: Modifier) {
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (hasFocus) FOCUSED_TILE_SCALE else 1f, label = "tile scale")
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(TileHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(if (hasFocus) TubepipeColors.SurfaceVariant else TubepipeColors.Surface, shape)
            .then(if (hasFocus) Modifier.border(3.dp, TubepipeColors.FocusBorder, shape) else Modifier)
            .onFocusChanged { hasFocus = it.hasFocus }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.TopStart)
                .background(
                    if (hasFocus) TubepipeColors.PrimaryContainer else TubepipeColors.SurfaceVariant,
                    RoundedCornerShape(7.dp),
                ),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = TubepipeColors.OnSurface,
                modifier = Modifier.size(16.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TubepipeColors.OnSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopEnd),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = stringResource(category.titleResource),
                style = MaterialTheme.typography.titleMedium,
                color = TubepipeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = TubepipeColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The one-line "what is set now" text under each tile name. */
@Composable
private fun categorySummary(
    category: SettingsCategory,
    settings: AppSettings,
    countryName: String,
    versionName: String,
): String {
    val parts: List<String> = when (category) {
        SettingsCategory.Playback -> listOf(
            speedLabel(settings.defaultSpeed),
            stringResource(R.string.settings_summary_seek, settings.seekStepSeconds),
            stringResource(autoplaySummaryResource(settings.autoplayNext)),
        )
        SettingsCategory.QualityAndAudio -> listOf(
            stringResource(settings.defaultQuality.labelResource()),
            stringResource(settings.preferredCodec.labelResource()),
            audioSummary(settings.preferredAudioLanguage),
        )
        SettingsCategory.Captions -> listOf(
            stringResource(if (settings.captionsEnabled) R.string.settings_on else R.string.settings_off),
            stringResource(
                R.string.settings_summary_caption_size,
                stringResource(settings.captionSize.labelResource()),
            ),
        )
        SettingsCategory.SponsorBlock -> sponsorBlockSummary(settings)
        SettingsCategory.HomeScreen -> {
            val shownCount = settings.visibleHomeSections.size
            listOf(pluralStringResource(R.plurals.settings_summary_home_rows, shownCount, shownCount))
        }
        SettingsCategory.ContentFilters -> listOf(contentFilterSummary(settings))
        SettingsCategory.HistoryAndPrivacy -> listOf(historySummary(settings))
        SettingsCategory.RegionAndEngine -> listOf(
            countryName,
            stringResource(settings.playerClientName.engineLabelResource()),
        )
        SettingsCategory.About -> listOf(
            stringResource(R.string.settings_summary_about, stringResource(R.string.app_name), versionName),
        )
    }
    return parts.joinToString(separator = stringResource(R.string.settings_summary_separator))
}

@StringRes
private fun autoplaySummaryResource(autoplayNext: Boolean): Int =
    if (autoplayNext) R.string.settings_summary_autoplay_on else R.string.settings_summary_autoplay_off

@Composable
private fun audioSummary(languageCode: String): String {
    if (languageCode == AppSettings.ORIGINAL_AUDIO_LANGUAGE) {
        return stringResource(R.string.settings_summary_audio_original)
    }
    val languageName = AudioLanguageChoices.firstOrNull { it.first == languageCode }?.second
        ?.let { stringResource(it) }
        ?: languageCode
    return stringResource(R.string.settings_summary_audio_language, languageName)
}

@Composable
private fun sponsorBlockSummary(settings: AppSettings): List<String> {
    val onOrOff = stringResource(if (settings.sponsorBlockEnabled) R.string.settings_on else R.string.settings_off)
    if (!settings.sponsorBlockEnabled) return listOf(onOrOff)
    return listOf(
        onOrOff,
        stringResource(
            R.string.settings_summary_categories,
            settings.sponsorBlockCategories.size,
            SponsorBlockCategory.entries.size,
        ),
    )
}

@Composable
private fun contentFilterSummary(settings: AppSettings): String {
    val hiddenNames = buildList {
        if (settings.hideShorts) add(stringResource(R.string.settings_summary_hiding_shorts))
        if (settings.hideUpcoming) add(stringResource(R.string.settings_summary_hiding_upcoming))
        if (settings.hidePaid) add(stringResource(R.string.settings_summary_hiding_paid))
    }
    if (hiddenNames.isEmpty()) return stringResource(R.string.settings_summary_hiding_nothing)
    return stringResource(R.string.settings_summary_hiding, hiddenNames.joinToString(", "))
}

@Composable
private fun historySummary(settings: AppSettings): String = stringResource(
    when {
        settings.watchHistoryEnabled && settings.searchHistoryEnabled -> R.string.settings_summary_history_both
        settings.watchHistoryEnabled -> R.string.settings_summary_history_watch
        settings.searchHistoryEnabled -> R.string.settings_summary_history_search
        else -> R.string.settings_summary_history_none
    },
)
