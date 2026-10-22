package org.tubepipe.tv.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.R

/** Asks for a playlist name with the on-screen keyboard; [onCreate] gets the raw text, so the caller trims it. */
@Composable
internal fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val canCreate = name.isNotBlank()
    val submit = {
        if (canCreate) {
            onCreate(name)
            onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(
                text = stringResource(R.string.library_new_playlist),
                style = MaterialTheme.typography.titleLarge,
                color = TubepipeColors.OnSurface,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TubepipeColors.SurfaceVariant, RoundedCornerShape(8.dp))
                    .border(
                        width = if (fieldFocused) 3.dp else 1.dp,
                        color = if (fieldFocused) TubepipeColors.FocusBorder else TubepipeColors.BorderVariant,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(16.dp),
            ) {
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_new_playlist_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TubepipeColors.OnSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TubepipeColors.OnSurface),
                    cursorBrush = SolidColor(TubepipeColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .onFocusChanged { fieldFocused = it.isFocused },
                )
            }
            Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.library_create))
            }
        }
        LaunchedEffect(Unit) { fieldFocus.requestFocus() }
    }
}

@Composable
internal fun DialogSurface(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 3),
        modifier = Modifier
            .width(480.dp)
            .background(TubepipeColors.Surface, RoundedCornerShape(12.dp))
            .padding(TubepipeSpacing.Grid * 6),
    ) {
        content()
    }
}
