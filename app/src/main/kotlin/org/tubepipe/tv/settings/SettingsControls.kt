package org.tubepipe.tv.settings

/**
 * The choice [step] places away from [current] in [choices], clamped at both ends. A [current] that is not in the
 * list counts as the nearest choice, so values stored by an older version still step sensibly.
 */
internal fun <T : Number> steppedChoice(choices: List<T>, current: T, step: Int): T {
    TODO("Settings slice implements this; see SettingsControlsTest")
}
