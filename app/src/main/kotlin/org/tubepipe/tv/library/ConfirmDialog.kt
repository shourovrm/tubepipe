package org.tubepipe.tv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.R

/**
 * Asks before an action the user cannot take back. Focus starts on the confirm button so one
 * Centre press confirms; Back or Cancel closes the dialog without acting.
 */
@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val confirmFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 3),
            modifier = Modifier
                .width(480.dp)
                .background(TubepipeColors.Surface, RoundedCornerShape(12.dp))
                .padding(TubepipeSpacing.Grid * 6),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = TubepipeColors.OnSurface)
            Text(text = message, style = MaterialTheme.typography.bodyLarge, color = TubepipeColors.OnSurfaceVariant)
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                modifier = Modifier.fillMaxWidth().focusRequester(confirmFocus),
            ) {
                Text(text = confirmLabel)
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.library_cancel))
            }
        }
        LaunchedEffect(Unit) { confirmFocus.requestFocus() }
    }
}
