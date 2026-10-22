package org.tubepipe.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.util.Locale
import kotlinx.coroutines.launch
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.R
import org.tubepipe.tv.data.AppSettings
import org.tubepipe.tv.data.HomeSection
import org.tubepipe.tv.navigation.AppNavigator

private const val PLAYER_CLIENT_VISIONOS = "visionos"
private const val PLAYER_CLIENT_MWEB = "mweb"

@Composable
fun SettingsScreen(container: AppContainer, navigator: AppNavigator) {
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.settings, Locale.getDefault())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    if (settings == null) {
        LoadingView()
        return
    }

    var choosingCountry by remember { mutableStateOf(false) }
    val clearScope = rememberCoroutineScope()
    val clearActions = remember(container) {
        ClearActions(
            clearWatchHistory = { clearScope.launch { container.database.historyDao().clear() } },
            clearPlaybackPositions = { clearScope.launch { container.database.historyDao().clearPositions() } },
            clearSearchHistory = { clearScope.launch { container.database.searchDao().clearSearchHistory() } },
        )
    }
    val versionName = rememberAppVersionName()
    val currentCountryName = state.countries.firstOrNull { it.code == settings.contentCountry }?.name
        ?: settings.contentCountry

    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = TubepipeSpacing.SafeMarginHorizontal,
            vertical = TubepipeSpacing.SafeMarginVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 2),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "country") {
            SettingRow(
                headline = stringResource(R.string.settings_content_country),
                supporting = stringResource(R.string.settings_content_country_supporting),
                trailing = currentCountryName,
                onClick = { choosingCountry = true },
            )
        }
        item(key = "player-title") { SectionTitle(stringResource(R.string.settings_player)) }
        item(key = "player-visionos") {
            PlayerOption(
                label = stringResource(R.string.settings_player_visionos),
                selected = settings.playerClientName == PLAYER_CLIENT_VISIONOS,
                onClick = { viewModel.setPlayerClient(PLAYER_CLIENT_VISIONOS) },
            )
        }
        item(key = "player-mweb") {
            PlayerOption(
                label = stringResource(R.string.settings_player_mweb),
                selected = settings.playerClientName == PLAYER_CLIENT_MWEB,
                onClick = { viewModel.setPlayerClient(PLAYER_CLIENT_MWEB) },
            )
        }
        playerPreferenceItems(settings, viewModel)
        item(key = "sections-title") { SectionTitle(stringResource(R.string.settings_home_sections)) }
        itemsIndexed(settings.homeSectionOrder, key = { _, section -> "section-${section.name}" }) { _, section ->
            HomeSectionRow(
                section = section,
                hidden = section in settings.hiddenHomeSections,
                onToggleHidden = { viewModel.toggleSectionHidden(section) },
                onMoveUp = { viewModel.moveSectionUp(section) },
                onMoveDown = { viewModel.moveSectionDown(section) },
            )
        }
        historyAndSearchItems(settings, viewModel, clearActions)
        contentFilterItems(settings, viewModel)
        sponsorBlockItems(settings, viewModel)
        aboutItems(versionName)
    }

    if (choosingCountry) {
        CountryDialog(
            countries = state.countries,
            settings = settings,
            onChosen = { countryCode ->
                viewModel.setContentCountry(countryCode)
                choosingCountry = false
            },
            onDismiss = { choosingCountry = false },
        )
    }
}

/**
 * Every About line is a focusable row, not plain text: the list only scrolls to what the
 * D-pad focus reaches, so plain text at the bottom would be unreadable.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.aboutItems(versionName: String) {
    item(key = "about-title") { SectionTitle(stringResource(R.string.settings_about)) }
    item(key = "about-version") {
        SettingRow(
            headline = stringResource(R.string.settings_about_version, stringResource(R.string.app_name), versionName),
        )
    }
    item(key = "about-license") { SettingRow(headline = stringResource(R.string.settings_about_license)) }
    item(key = "about-credits") { SettingRow(headline = stringResource(R.string.settings_about_credits)) }
    item(key = "about-newpipe") {
        SettingRow(
            headline = stringResource(R.string.settings_about_newpipe),
            supporting = stringResource(R.string.settings_about_newpipe_url),
        )
    }
    item(key = "about-pipepipe") {
        SettingRow(
            headline = stringResource(R.string.settings_about_pipepipe),
            supporting = stringResource(R.string.settings_about_pipepipe_url),
        )
    }
    item(key = "about-source") { SettingRow(headline = stringResource(R.string.settings_about_source)) }
}

@Composable
private fun rememberAppVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        // BuildConfig is off in this module, so the version comes from the installed package.
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = TubepipeColors.OnSurface,
        modifier = Modifier.padding(top = TubepipeSpacing.Gutter, bottom = TubepipeSpacing.Grid * 2),
    )
}

/** A 64dp two-line list row (48dp when it has no supporting text), per docs/design-system.md. */
@Composable
internal fun SettingRow(
    headline: String,
    supporting: String? = null,
    trailing: String? = null,
    onClick: () -> Unit = {},
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(text = headline, maxLines = 1) },
        supportingContent = supporting?.let { text -> { Text(text = text, maxLines = 1) } },
        trailingContent = trailing?.let { text -> { Text(text = text) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlayerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    SettingRow(
        headline = label,
        trailing = if (selected) stringResource(R.string.settings_selected_marker) else null,
        onClick = onClick,
    )
}

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 3),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(section.labelResource()),
                style = MaterialTheme.typography.bodyLarge,
                color = if (hidden) TubepipeColors.OnSurfaceVariant else TubepipeColors.OnSurface,
                maxLines = 1,
            )
            if (hidden) {
                Text(
                    text = stringResource(R.string.settings_hidden_marker),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TubepipeColors.OnSurfaceVariant,
                )
            }
        }
        Button(onClick = onToggleHidden) {
            Text(text = stringResource(if (hidden) R.string.settings_show else R.string.settings_hide))
        }
        Button(onClick = onMoveUp) { Text(text = stringResource(R.string.settings_move_up)) }
        Button(onClick = onMoveDown) { Text(text = stringResource(R.string.settings_move_down)) }
    }
}

private fun HomeSection.labelResource(): Int = when (this) {
    HomeSection.ContinueWatching -> R.string.settings_section_continue_watching
    HomeSection.Recommended -> R.string.settings_section_recommended
    HomeSection.Mix -> R.string.settings_section_mix
    HomeSection.NewFromSubscriptions -> R.string.settings_section_new_from_subscriptions
    HomeSection.TrendingMusic -> R.string.settings_section_trending_music
    HomeSection.TrendingMovies -> R.string.settings_section_trending_movies
}

@Composable
private fun CountryDialog(
    countries: List<CountryOption>,
    settings: AppSettings,
    onChosen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val currentCountryFocus = remember { FocusRequester() }
    val currentIndex = countries.indexOfFirst { it.code == settings.contentCountry }.coerceAtLeast(0)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .height(560.dp)
                .background(TubepipeColors.Surface, RoundedCornerShape(12.dp))
                .padding(TubepipeSpacing.Grid * 4),
        ) {
            Text(
                text = stringResource(R.string.settings_content_country),
                style = MaterialTheme.typography.titleLarge,
                color = TubepipeColors.OnSurface,
                modifier = Modifier.padding(bottom = TubepipeSpacing.Grid * 3),
            )
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid)) {
                itemsIndexed(countries, key = { _, country -> country.code }) { index, country ->
                    val isCurrent = country.code == settings.contentCountry
                    ListItem(
                        selected = isCurrent,
                        onClick = { onChosen(country.code) },
                        headlineContent = { Text(text = country.name, maxLines = 1) },
                        trailingContent = if (isCurrent) {
                            { Text(text = stringResource(R.string.settings_country_current)) }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index ==
                                    currentIndex
                                ) {
                                    Modifier.focusRequester(currentCountryFocus)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            // Scroll first so the current row is composed, then wait one frame for it to attach before focusing.
            listState.scrollToItem(currentIndex)
            withFrameNanos { }
            currentCountryFocus.requestFocus()
        }
    }
}
