package org.tubepipe.core.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// Roboto is the device sans, so no font file ships. Display and headline use the condensed
// family at weight 600 (TypefaceTokens.Brand in docs/design-system.md).
private val BrandFamily = FontFamily.SansSerif
private val BrandCondensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

private val defaultTypography = Typography()

val TubepipeTypography = Typography(
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = BrandCondensed, fontWeight = FontWeight.SemiBold),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = BrandCondensed,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = BrandCondensed, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = BrandFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = BrandFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BrandFamily, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(
        fontFamily = BrandFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
)
