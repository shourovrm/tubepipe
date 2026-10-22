package org.tubepipe.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.ui.R
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    CenteredMessage(message = stringResource(R.string.state_loading), modifier = modifier)
}

@Composable
fun EmptyView(modifier: Modifier = Modifier, message: String = stringResource(R.string.state_empty)) {
    CenteredMessage(message = message, modifier = modifier)
}

/** Shows what failed and a Retry button that takes focus, so the remote can act on it at once. */
@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val retryFocus = remember { FocusRequester() }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 4),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge, color = TubepipeColors.OnSurface)
            Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                Text(text = stringResource(R.string.state_retry))
            }
        }
    }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }
}

@Composable
private fun CenteredMessage(message: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = TubepipeColors.OnSurfaceVariant)
    }
}
