package org.tubepipe.tv

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level drawer entries, in the order docs/decisions/architecture.md fixes ("Product structure"). */
enum class Destination(@param:StringRes val labelResource: Int, val icon: ImageVector) {
    Home(R.string.destination_home, Icons.Filled.Home),
    Trending(R.string.destination_trending, Icons.Filled.Star),
    Subscriptions(R.string.destination_subscriptions, Icons.Filled.AccountBox),
    Search(R.string.destination_search, Icons.Filled.Search),
    Library(R.string.destination_library, Icons.AutoMirrored.Filled.List),
    Settings(R.string.destination_settings, Icons.Filled.Settings),
    ;

    companion object {
        /** Android TV guidance allows seven drawer slots; the seventh is reserved for a pinned saved search. */
        const val MAXIMUM_DRAWER_SLOTS = 7
    }
}
