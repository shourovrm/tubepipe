package org.tubepipe.tv.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlin.math.abs
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.R
import org.tubepipe.tv.library.ConfirmDialog

/**
 * The choice [step] places away from [current] in [choices], clamped at both ends. A [current] that is not in the
 * list counts as the nearest choice, so values stored by an older version still step sensibly.
 */
internal fun <T : Number> steppedChoice(choices: List<T>, current: T, step: Int): T {
    require(choices.isNotEmpty()) { "A slider needs at least one choice." }
    val currentValue = current.toDouble()
    val nearestIndex = choices.indices.minBy { index -> abs(choices[index].toDouble() - currentValue) }
    val targetIndex = (nearestIndex + step).coerceIn(choices.indices)
    return choices[targetIndex]
}

/** The title and explanation the help panel shows while a row has focus. */
internal class HelpText(val title: String, val body: String)

/** Where a focused row reports its [HelpText]; the page scaffold supplies the real receiver. */
internal val LocalHelpSink = staticCompositionLocalOf<(HelpText) -> Unit> { {} }

private val RowShape = RoundedCornerShape(6.dp)
private val FocusRingWidth = 3.dp
private val RowMinHeight = 48.dp
private val ControlTextSize = 16.sp
private val ChipTextSize = 14.sp
private val SwitchOffColor = Color(0xFF444444)
private val SliderTrackColor = Color(0xFF333333)

private fun Modifier.focusRing(focused: Boolean, shape: Shape): Modifier =
    if (focused) border(FocusRingWidth, TubepipeColors.FocusBorder, shape) else this

/**
 * The shared look of a settings row: dark surface, lighter when focused, with the red ring. It tells the help
 * panel about itself whenever focus lands on the row or on a control inside it. [interaction] carries the
 * focusable behaviour of the row; rows whose chips are the focus targets pass none.
 */
@Composable
private fun SettingsRowFrame(
    help: HelpText,
    interaction: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val helpSink = LocalHelpSink.current
    var hasFocus by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid * 3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .background(if (hasFocus) TubepipeColors.SurfaceVariant else TubepipeColors.Surface, RowShape)
            .focusRing(hasFocus, RowShape)
            .onFocusChanged { focusState ->
                hasFocus = focusState.hasFocus
                if (focusState.hasFocus) helpSink(help)
            }
            .then(interaction)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun RowScope.RowName(name: String, icon: ImageVector?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.weight(1f),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TubepipeColors.OnSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = name,
            fontSize = ControlTextSize,
            color = TubepipeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row with an on/off switch. Centre flips it. */
@Composable
internal fun SettingsToggleRow(
    name: String,
    help: HelpText,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
) {
    SettingsRowFrame(
        help = help,
        interaction = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        RowName(name, icon)
        SwitchTrack(checked)
    }
}

@Composable
private fun SwitchTrack(checked: Boolean) {
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 2.dp, label = "switch thumb")
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 20.dp)
            .background(if (checked) TubepipeColors.Primary else SwitchOffColor, RoundedCornerShape(10.dp)),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset, y = 2.dp)
                .size(16.dp)
                .background(if (checked) TubepipeColors.OnPrimary else TubepipeColors.OnSurfaceVariant, CircleShape),
        )
    }
}

/**
 * A row that steps through [choices] with Left and Right and saves each step at once. Left and Right are consumed
 * even at the ends, so a held key never throws focus into the drawer.
 */
@Composable
internal fun <T : Number> SettingsSliderRow(
    name: String,
    help: HelpText,
    choices: List<T>,
    current: T,
    valueLabel: String,
    onChosen: (T) -> Unit,
    icon: ImageVector? = null,
) {
    val shownValue = steppedChoice(choices, current, step = 0)
    val fraction = choices.indexOf(shownValue).toFloat() / (choices.size - 1).coerceAtLeast(1)
    SettingsRowFrame(
        help = help,
        interaction = Modifier
            .onKeyEvent { keyEvent ->
                val step = when (keyEvent.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onKeyEvent false
                }
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val next = steppedChoice(choices, current, step)
                    if (next != shownValue) onChosen(next)
                }
                true
            }
            .focusable(),
    ) {
        RowName(name, icon)
        SliderTrack(fraction = fraction, modifier = Modifier.width(150.dp))
        Text(
            text = valueLabel,
            fontSize = ControlTextSize,
            color = TubepipeColors.OnSurface,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun SliderTrack(fraction: Float, modifier: Modifier = Modifier) {
    val knobSize = 13.dp
    Box(modifier = modifier.height(knobSize), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(SliderTrackColor, RoundedCornerShape(3.dp)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .background(TubepipeColors.Primary, RoundedCornerShape(3.dp)),
        )
        // Two weighted spacers around the knob place it at the fraction without measuring the track width.
        Row(Modifier.fillMaxWidth()) {
            if (fraction > 0f) Spacer(Modifier.weight(fraction))
            Box(Modifier.size(knobSize).background(TubepipeColors.OnSurface, CircleShape))
            if (fraction < 1f) Spacer(Modifier.weight(1f - fraction))
        }
    }
}

/** One choice in a segmented row or dropdown: the value it stands for and the words shown for it. */
internal class PickerOption<T>(val value: T, val label: String)

/**
 * A row of chips for a short fixed list. Each chip is its own focus target, so Left and Right move between chips
 * and Centre picks. [chipText] and [chipFontSize] let a row draw something other than the label, such as the "Aa"
 * samples; the label stays the spoken description.
 */
@Composable
internal fun <T> SettingsSegmentedRow(
    name: String,
    help: HelpText,
    options: List<PickerOption<T>>,
    current: T,
    onChosen: (T) -> Unit,
    icon: ImageVector? = null,
    chipText: (PickerOption<T>) -> String = { option -> option.label },
    chipFontSize: (PickerOption<T>) -> TextUnit = { ChipTextSize },
) {
    SettingsRowFrame(help = help) {
        RowName(name, icon)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(TubepipeColors.Background, RoundedCornerShape(14.dp))
                .padding(4.dp),
        ) {
            options.forEach { option ->
                ChipButton(
                    text = chipText(option),
                    selected = option.value == current,
                    onClick = { onChosen(option.value) },
                    fontSize = chipFontSize(option),
                    modifier = Modifier.semantics { contentDescription = option.label },
                )
            }
        }
    }
}

/** A pill-shaped, focusable button. Wine when [selected], clear otherwise, with the red ring when focused. */
@Composable
internal fun ChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    fontSize: TextUnit = ChipTextSize,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 24.dp)
            .background(if (selected) TubepipeColors.PrimaryContainer else Color.Transparent, shape)
            .focusRing(hasFocus, shape)
            .onFocusChanged { hasFocus = it.hasFocus }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = fontSize, color = TubepipeColors.OnSurface, maxLines = 1)
    }
}

/** A row that shows the current choice and, on Centre, opens a list of every choice with a check on the current. */
@Composable
internal fun <T> SettingsDropdownRow(
    name: String,
    help: HelpText,
    options: List<PickerOption<T>>,
    current: T,
    onChosen: (T) -> Unit,
    icon: ImageVector? = null,
    isSearchable: Boolean = false,
) {
    var isOpen by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.value == current }?.label.orEmpty()
    SettingsRowFrame(help = help, interaction = Modifier.clickable(role = Role.Button) { isOpen = true }) {
        RowName(name, icon)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(TubepipeColors.Background, RoundedCornerShape(5.dp))
                .padding(start = 10.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(text = currentLabel, fontSize = ChipTextSize, color = TubepipeColors.OnSurface, maxLines = 1)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = TubepipeColors.OnSurface,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    if (isOpen) {
        PickerDialog(
            title = name,
            options = options,
            current = current,
            isSearchable = isSearchable,
            onChosen = { value ->
                onChosen(value)
                isOpen = false
            },
            onDismiss = { isOpen = false },
        )
    }
}

/**
 * A row that runs an action the user cannot take back, after a confirmation dialog. The dialog is the shared
 * [ConfirmDialog], which starts focus on the confirm button.
 */
@Composable
internal fun SettingsConfirmActionRow(
    name: String,
    help: HelpText,
    confirmMessage: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
) {
    var isConfirming by remember { mutableStateOf(false) }
    SettingsRowFrame(help = help, interaction = Modifier.clickable(role = Role.Button) { isConfirming = true }) {
        RowName(name, icon = null)
        Text(text = confirmLabel, fontSize = ChipTextSize, color = TubepipeColors.Error, maxLines = 1)
    }
    if (isConfirming) {
        ConfirmDialog(
            title = name,
            message = confirmMessage,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            onDismiss = { isConfirming = false },
        )
    }
}

/** A focusable row that only shows information; focusing it puts its explanation in the help panel. */
@Composable
internal fun SettingsInfoRow(name: String, help: HelpText, value: String? = null) {
    SettingsRowFrame(help = help, interaction = Modifier.focusable()) {
        RowName(name, icon = null)
        if (value != null) {
            Text(text = value, fontSize = ControlTextSize, color = TubepipeColors.OnSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
internal fun SettingsGroupLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = TubepipeColors.FocusBorder,
        modifier = Modifier.padding(start = 4.dp, top = 9.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<PickerOption<T>>,
    current: T,
    isSearchable: Boolean,
    onChosen: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val currentFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    val shownOptions = if (isSearchable) matchingOptions(options, query) else options
    val currentIndex = shownOptions.indexOfFirst { it.value == current }

    fun focusFirstResult() {
        if (shownOptions.isNotEmpty()) {
            keyboardController?.hide()
            firstResultFocus.requestFocus()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = 440.dp)
                .background(TubepipeColors.SurfaceVariant, RoundedCornerShape(6.dp))
                .padding(TubepipeSpacing.Grid * 4),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TubepipeColors.OnSurface,
                modifier = Modifier.padding(bottom = TubepipeSpacing.Grid * 3),
            )
            if (isSearchable) {
                PickerSearchField(
                    query = query,
                    onQueryChange = { newQuery ->
                        query = newQuery
                        listState.requestScrollToItem(0)
                    },
                    onDone = ::focusFirstResult,
                    modifier = Modifier
                        .focusRequester(searchFocus)
                        .focusProperties {
                            down =
                                if (shownOptions.isEmpty()) FocusRequester.Default else firstResultFocus
                        }
                        .padding(bottom = TubepipeSpacing.Grid * 3),
                )
                if (shownOptions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_picker_no_matches),
                        fontSize = ChipTextSize,
                        color = TubepipeColors.OnSurfaceVariant,
                    )
                }
            }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(TubepipeSpacing.Grid)) {
                itemsIndexed(shownOptions, key = { _, option -> option.label }) { index, option ->
                    val isCurrent = index == currentIndex
                    val focusModifier = when {
                        index == 0 && isSearchable -> Modifier.focusRequester(firstResultFocus)
                        isCurrent -> Modifier.focusRequester(currentFocus)
                        else -> Modifier
                    }
                    ListItem(
                        selected = isCurrent,
                        onClick = { onChosen(option.value) },
                        headlineContent = { Text(text = option.label, fontSize = ChipTextSize, maxLines = 1) },
                        trailingContent = if (isCurrent) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.settings_selected_marker),
                                    tint = TubepipeColors.Primary,
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth().then(focusModifier),
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            if (isSearchable) {
                // Typing is the fast way through 250 countries, so the field takes focus and the keyboard opens.
                withFrameNanos { }
                searchFocus.requestFocus()
                keyboardController?.show()
            } else {
                // Scroll first so the current row is composed, then wait one frame for it to attach before focusing.
                listState.scrollToItem(currentIndex.coerceAtLeast(0))
                withFrameNanos { }
                currentFocus.requestFocus()
            }
        }
    }
}

/** The filter box at the top of a searchable picker. A remote has no tap, so Centre opens the keyboard. */
@Composable
private fun PickerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(5.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = ChipTextSize, color = TubepipeColors.OnSurface),
        cursorBrush = SolidColor(TubepipeColors.Primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .onPreviewKeyEvent { keyEvent ->
                val isCentreRelease = keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyUp
                if (isCentreRelease) {
                    keyboardController?.show()
                }
                isCentreRelease
            },
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TubepipeColors.Background, shape)
                    .border(
                        width = if (isFocused) 3.dp else 1.dp,
                        color = if (isFocused) TubepipeColors.FocusBorder else TubepipeColors.BorderVariant,
                        shape = shape,
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_picker_search_hint),
                        fontSize = ChipTextSize,
                        color = TubepipeColors.OnSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** A chip that flips on Centre: a dot in [dotColor], the name, and a red check while on. */
@Composable
internal fun SettingsToggleChip(
    name: String,
    help: HelpText,
    dotColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val helpSink = LocalHelpSink.current
    var hasFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (hasFocus) TubepipeColors.SurfaceVariant else TubepipeColors.Surface, shape)
            .focusRing(hasFocus, shape)
            .onFocusChanged { focusState ->
                hasFocus = focusState.hasFocus
                if (focusState.hasFocus) helpSink(help)
            }
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        // A dimmed dot marks a category that is off, as the seek bar will not show it.
        Box(
            Modifier
                .size(9.dp)
                .background(if (checked) dotColor else dotColor.copy(alpha = 0.3f), CircleShape),
        )
        Text(
            text = name,
            fontSize = ChipTextSize,
            color = if (checked) TubepipeColors.OnSurface else Color(0xFF777777),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = TubepipeColors.Primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** A row with a name and a few chip buttons on the right. Each chip is a focus target; the row only lights up. */
@Composable
internal fun SettingsChipsRow(
    name: String,
    help: HelpText,
    dimmed: Boolean = false,
    chips: @Composable RowScope.() -> Unit,
) {
    SettingsRowFrame(help = help) {
        Text(
            text = name,
            fontSize = ControlTextSize,
            color = if (dimmed) TubepipeColors.OnSurfaceVariant else TubepipeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = chips)
    }
}
