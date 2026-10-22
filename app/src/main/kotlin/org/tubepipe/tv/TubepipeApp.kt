package org.tubepipe.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.navigation.AppNavigator
import org.tubepipe.tv.navigation.Screen

/**
 * App shell: the drawer around top-level screens, full screen for channels and the player.
 * [content] draws the current screen; [onExit] runs on the second Back on Home.
 */
@Composable
fun TubepipeApp(navigator: AppNavigator, onExit: () -> Unit, content: @Composable (Screen) -> Unit) {
    BackHandler {
        if (!navigator.back()) {
            onExit()
        }
    }

    val screen = navigator.current
    if (screen is Screen.Top) {
        NavigationDrawer(
            drawerContent = {
                DrawerItems(selected = screen.destination, onSelect = navigator::openDestination)
            },
        ) {
            content(screen)
        }
    } else {
        content(screen)
    }
}

@Composable
private fun NavigationDrawerScope.DrawerItems(selected: Destination, onSelect: (Destination) -> Unit) {
    val focusBorder = Border(BorderStroke(2.dp, TubepipeColors.FocusBorder))
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = TubepipeSpacing.SafeMarginVertical, horizontal = TubepipeSpacing.Grid * 2)
            .selectableGroup(),
    ) {
        for (destination in Destination.entries) {
            NavigationDrawerItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                leadingContent = { Icon(imageVector = destination.icon, contentDescription = null) },
                // Red is the focus cue and wine marks the current screen (docs/design-system.md).
                colors = NavigationDrawerItemDefaults.colors(
                    focusedContainerColor = TubepipeColors.SurfaceVariant,
                    focusedContentColor = TubepipeColors.OnSurface,
                    selectedContainerColor = TubepipeColors.PrimaryContainer,
                    selectedContentColor = TubepipeColors.OnPrimaryContainer,
                    focusedSelectedContainerColor = TubepipeColors.PrimaryContainer,
                    focusedSelectedContentColor = TubepipeColors.OnPrimaryContainer,
                ),
                border = NavigationDrawerItemDefaults.border(
                    focusedBorder = focusBorder,
                    focusedSelectedBorder = focusBorder,
                ),
            ) {
                Text(text = stringResource(destination.labelResource))
            }
        }
    }
}

/** Stand-in for a screen that a later change builds. */
@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = TubepipeColors.OnSurface)
            Text(
                text = stringResource(R.string.placeholder_not_built),
                style = MaterialTheme.typography.bodyLarge,
                color = TubepipeColors.OnSurfaceVariant,
            )
        }
    }
}
