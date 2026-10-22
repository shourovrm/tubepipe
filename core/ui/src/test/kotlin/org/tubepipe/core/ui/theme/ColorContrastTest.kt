package org.tubepipe.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design gate: text must stay readable at 3 m and the focus border must be visible on every
 * surface it can land on. Thresholds are WCAG: 4.5:1 for body text, 3:1 for non-text cues.
 */
class ColorContrastTest {
    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertContrastAtLeast(minimum: Double, foreground: Color, background: Color, pair: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$pair contrast is $ratio, needs at least $minimum", ratio >= minimum)
    }

    @Test
    fun bodyTextIsReadableOnEverySurface() {
        val surfaces = mapOf(
            "background" to TubepipeColors.Background,
            "surface" to TubepipeColors.Surface,
            "surfaceVariant" to TubepipeColors.SurfaceVariant,
        )
        for ((name, surface) in surfaces) {
            assertContrastAtLeast(4.5, TubepipeColors.OnSurface, surface, "onSurface on $name")
            assertContrastAtLeast(4.5, TubepipeColors.OnSurfaceVariant, surface, "onSurfaceVariant on $name")
        }
    }

    @Test
    fun focusBorderIsVisibleOnEverySurface() {
        assertContrastAtLeast(3.0, TubepipeColors.FocusBorder, TubepipeColors.Background, "focus border on background")
        assertContrastAtLeast(3.0, TubepipeColors.FocusBorder, TubepipeColors.Surface, "focus border on surface")
        assertContrastAtLeast(
            3.0,
            TubepipeColors.FocusBorder,
            TubepipeColors.SurfaceVariant,
            "focus border on surfaceVariant",
        )
    }

    @Test
    fun primaryButtonLabelIsReadable() {
        assertContrastAtLeast(4.5, TubepipeColors.OnPrimary, TubepipeColors.PrimaryContainer, "onPrimaryContainer")
        assertContrastAtLeast(3.0, TubepipeColors.OnPrimary, TubepipeColors.Primary, "onPrimary (large label)")
    }
}
