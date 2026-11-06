package org.tubepipe.tv.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.tv.R
import org.tubepipe.tv.common.TextInputDialog

/**
 * The Parental sub-page that lists the blocked words. The first row adds one with the on-screen keyboard; Centre
 * on a word asks before removing it.
 */
@Composable
internal fun BlockedWordsPage(words: Set<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var isAdding by rememberSaveable { mutableStateOf(false) }
    SettingsPageScaffold(stringResource(R.string.settings_blocked_words)) {
        Text(
            text = stringResource(R.string.settings_blocked_words_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = TubepipeColors.OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SettingsActionRow(
            name = stringResource(R.string.settings_add_blocked_word),
            value = null,
            icon = Icons.Filled.Add,
            onClick = { isAdding = true },
        )
        if (words.isNotEmpty()) {
            SettingsGroupLabel(stringResource(R.string.settings_blocked_words_group))
        }
        for (word in words.sorted()) {
            SettingsConfirmActionRow(
                name = word,
                confirmMessage = stringResource(R.string.settings_remove_blocked_word_message),
                confirmLabel = stringResource(R.string.settings_remove),
                onConfirm = { onRemove(word) },
            )
        }
    }
    if (isAdding) {
        TextInputDialog(
            title = stringResource(R.string.settings_add_blocked_word),
            hint = stringResource(R.string.settings_add_blocked_word_hint),
            confirmLabel = stringResource(R.string.settings_add),
            onConfirm = onAdd,
            onDismiss = { isAdding = false },
        )
    }
}
