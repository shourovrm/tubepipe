package org.tubepipe.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import org.tubepipe.tv.navigation.Screen

private val NowPlayingThumbnailWidth = 40.dp
private val NowPlayingThumbnailHeight = 24.dp

/**
 * App shell: the drawer around top-level screens, full screen for channels and the player.
 * [content] draws the current screen; [onExit] runs on the second Back on Home.
 */
@Composable
fun TubepipeApp(container: AppContainer, onExit: () -> Unit, content: @Composable (Screen) -> Unit) {
    val navigator = container.navigator
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NavigationDrawerScope.DrawerItems(selected: Destination, onSelect: (Destination) -> Unit) {
    val focusBorder = Border(BorderStroke(2.dp, TubepipeColors.FocusBorder))
    // Without this, Left from the content lands on whichever item is geometrically nearest, not the current one.
    val selectedItemRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = TubepipeSpacing.SafeMarginVertical, horizontal = TubepipeSpacing.Grid * 2)
            // enter only applies to a focus group, so the column must be one.
            .focusProperties { enter = { selectedItemRequester } }
            .focusGroup()
            .selectableGroup(),
    ) {
        for (destination in Destination.entries) {
            val itemModifier = if (destination == selected) {
                Modifier.focusRequester(selectedItemRequester)
            } else {
                Modifier
            }
            NavigationDrawerItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                modifier = itemModifier,
                leadingContent = { Icon(imageVector = destination.icon, contentDescription = null) },
                colors = drawerItemColors(),
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

// Red is the focus cue and wine marks the current screen (docs/design-system.md).
@Composable
private fun drawerItemColors() = NavigationDrawerItemDefaults.colors(
    focusedContainerColor = TubepipeColors.SurfaceVariant,
    focusedContentColor = TubepipeColors.OnSurface,
    selectedContainerColor = TubepipeColors.PrimaryContainer,
    selectedContentColor = TubepipeColors.OnPrimaryContainer,
    focusedSelectedContainerColor = TubepipeColors.PrimaryContainer,
    focusedSelectedContentColor = TubepipeColors.OnPrimaryContainer,
)

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
