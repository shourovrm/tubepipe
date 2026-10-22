package org.tubepipe.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlinx.coroutines.launch
import org.tubepipe.core.ui.component.LoadingView
import org.tubepipe.tv.AppContainer
import org.tubepipe.tv.navigation.AppNavigator

/**
 * Settings: a hub of nine panels, each opening its own page. The open page lives in local state, not in the
 * navigator, so the shell's Back still means "leave Settings" and only this screen's Back means "back to the hub".
 */
@Composable
fun SettingsScreen(container: AppContainer, navigator: AppNavigator) {
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.settings, Locale.getDefault())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    // The tile the user came from; kept after the page closes so the hub can hand focus back to it.
    var lastOpenedCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }

    val clearScope = rememberCoroutineScope()
    val clearActions = remember(container) {
        ClearActions(
            clearWatchHistory = { clearScope.launch { container.database.historyDao().clear() } },
            clearPlaybackPositions = { clearScope.launch { container.database.historyDao().clearPositions() } },
            clearSearchHistory = { clearScope.launch { container.database.searchDao().clearSearchHistory() } },
        )
    }
    val versionName = rememberAppVersionName()

    // Registered after the shell's handler, so while a page is open this one wins and the shell's stays idle.
    BackHandler(enabled = openCategory != null) { openCategory = null }

    val settings = state.settings
    if (settings == null) {
        LoadingView()
        return
    }
    when (openCategory) {
        null -> SettingsHub(
            restoreFocusTo = lastOpenedCategory,
            onOpen = { category ->
                lastOpenedCategory = category
                openCategory = category
            },
        )
        SettingsCategory.Playback -> PlaybackPage(settings, viewModel)
        SettingsCategory.QualityAndAudio -> QualityAndAudioPage(settings, viewModel)
        SettingsCategory.Captions -> CaptionsPage(settings, viewModel)
        SettingsCategory.SponsorBlock -> SponsorBlockPage(settings, viewModel)
        SettingsCategory.HomeScreen -> HomeScreenPage(settings, viewModel)
        SettingsCategory.Parental -> ParentalPage(settings, viewModel)
        SettingsCategory.HistoryAndPrivacy -> HistoryAndPrivacyPage(settings, viewModel, clearActions)
        SettingsCategory.RegionAndEngine -> RegionAndEnginePage(settings, state.countries, viewModel)
        SettingsCategory.About -> AboutPage(versionName)
    }
}

@Composable
private fun rememberAppVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        // BuildConfig is off in this module, so the version comes from the installed package.
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}
