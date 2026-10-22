package org.tubepipe.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.R
import org.tubepipe.tv.library.ConfirmDialog

/** A row that shows an On or Off value and flips it on Centre. */
@Composable
internal fun ToggleRow(headline: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingRow(
        headline = headline,
        trailing = stringResource(if (checked) R.string.settings_on else R.string.settings_off),
        onClick = { onCheckedChange(!checked) },
    )
}

/** One choice in a [PickerRow]: the value it stands for and the words shown for it. */
internal class PickerOption<T>(val value: T, val label: String)

/** A row that shows the current choice and opens a D-pad dialog with every choice. */
@Composable
internal fun <T> PickerRow(headline: String, options: List<PickerOption<T>>, current: T, onChosen: (T) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.value == current }?.label.orEmpty()
    SettingRow(headline = headline, trailing = currentLabel, onClick = { isOpen = true })
    if (isOpen) {
        PickerDialog(
            title = headline,
            options = options,
            current = current,
            onChosen = { value ->
                onChosen(value)
                isOpen = false
            },
            onDismiss = { isOpen = false },
        )
    }
}

/** A row that, after a confirmation dialog, runs an action the user cannot take back. */
@Composable
internal fun ConfirmActionRow(
    headline: String,
    supporting: String,
    confirmMessage: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
) {
    var isConfirming by remember { mutableStateOf(false) }
    SettingRow(headline = headline, supporting = supporting, onClick = { isConfirming = true })
    if (isConfirming) {
        ConfirmDialog(
            title = headline,
            message = confirmMessage,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            onDismiss = { isConfirming = false },
        )
    }
}

@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<PickerOption<T>>,
    current: T,
    onChosen: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val currentFocus = remember { FocusRequester() }
    val currentIndex = options.indexOfFirst { it.value == current }.coerceAtLeast(0)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .background(TubepipeColors.Surface, RoundedCornerShape(12.dp))
                .padding(TubepipeSpacing.Grid * 4),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TubepipeColors.OnSurface,
                modifier = Modifier.padding(bottom = TubepipeSpacing.Grid * 3),
            )
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid),
                modifier = Modifier.padding(bottom = TubepipeSpacing.Grid),
            ) {
                itemsIndexed(options, key = { index, _ -> index }) { index, option ->
                    val isCurrent = index == currentIndex
                    ListItem(
                        selected = isCurrent,
                        onClick = { onChosen(option.value) },
                        headlineContent = { Text(text = option.label, maxLines = 1) },
                        trailingContent = if (isCurrent) {
                            { Text(text = stringResource(R.string.settings_country_current)) }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier),
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            // Scroll first so the current row is composed, then wait one frame for it to attach before focusing.
            listState.scrollToItem(currentIndex)
            withFrameNanos { }
            currentFocus.requestFocus()
        }
    }
}
