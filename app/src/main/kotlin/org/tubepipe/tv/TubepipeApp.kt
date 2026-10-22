package org.tubepipe.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeSpacing

@Composable
fun TubepipeApp() {
    var selectedDestination by rememberSaveable { mutableStateOf(Destination.Home) }

    NavigationDrawer(
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = TubepipeSpacing.SafeMarginVertical)
                    .selectableGroup(),
            ) {
                for (destination in Destination.entries) {
                    NavigationDrawerItem(
                        selected = destination == selectedDestination,
                        onClick = { selectedDestination = destination },
                        leadingContent = {
                            Icon(imageVector = destination.icon, contentDescription = null)
                        },
                    ) {
                        Text(text = stringResource(destination.labelResource))
                    }
                }
            }
        },
    ) {
        PlaceholderScreen(destination = selectedDestination)
    }
}

@Composable
private fun PlaceholderScreen(destination: Destination) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = TubepipeSpacing.SafeMarginHorizontal,
                vertical = TubepipeSpacing.SafeMarginVertical,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(destination.labelResource),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.placeholder_not_built),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
