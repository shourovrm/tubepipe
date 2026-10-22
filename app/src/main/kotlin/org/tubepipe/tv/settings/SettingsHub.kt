package org.tubepipe.tv.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.tubepipe.core.ui.theme.TubepipeColors
import org.tubepipe.core.ui.theme.TubepipeSpacing
import org.tubepipe.tv.R

/** The nine cards of the settings hub, in the order they appear (left to right, top to bottom). */
internal enum class SettingsCategory(
    @StringRes val titleResource: Int,
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color,
) {
    Playback(R.string.settings_page_playback, Icons.Filled.PlayArrow, Color(0xFF8E1B1B), Color(0xFFE53935)),
    QualityAndAudio(R.string.settings_page_quality, Icons.Filled.Build, Color(0xFF1F3A60), Color(0xFF3A6EA5)),
    Captions(R.string.settings_page_captions, Icons.Filled.Edit, Color(0xFF2E4A2E), Color(0xFF4F8A4F)),
    SponsorBlock(R.string.settings_sponsorblock, Icons.Filled.CheckCircle, Color(0xFF0E4A4A), Color(0xFF1C8C8C)),
    HomeScreen(R.string.settings_page_home, Icons.Filled.Home, Color(0xFF4A3A10), Color(0xFF9C7A1E)),
    Parental(R.string.settings_page_parental, Icons.Filled.Lock, Color(0xFF4A1040), Color(0xFF8E2A7E)),
    HistoryAndPrivacy(R.string.settings_page_history, Icons.Filled.Refresh, Color(0xFF333333), Color(0xFF555555)),
    RegionAndEngine(R.string.settings_page_region, Icons.Filled.LocationOn, Color(0xFF123A2A), Color(0xFF2A7A5A)),
    About(R.string.settings_about, Icons.Filled.Info, Color(0xFF222222), Color(0xFF3A3A3A)),
}

// Sizes are the 1920 px mockup halved, because the TV is 960 x 540 dp.
private const val CARDS_PER_ROW = 5
private val CardHeight = 115.dp
private val CardGap = 11.dp
private val CardCornerRadius = 11.dp
private val CardPadding = 13.dp
private val CardIconSize = 28.dp
private val FocusRingWidth = 2.dp
private val FocusRingOffset = 2.dp
private const val FOCUSED_CARD_SCALE = 1.05f

/**
 * The hub: colour cards, five to a row, one per [SettingsCategory], each showing only its icon and name.
 * When [restoreFocusTo] is set the card for that category takes focus, so Back from a page lands where the user left.
 */
@Composable
internal fun SettingsHub(restoreFocusTo: SettingsCategory?, onOpen: (SettingsCategory) -> Unit) {
    val cardFocusRequesters = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    Column(
        verticalArrangement = Arrangement.spacedBy(CardGap),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TubepipeSpacing.SafeMarginHorizontal, vertical = TubepipeSpacing.SafeMarginVertical),
    ) {
        Text(
            text = stringResource(R.string.destination_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = TubepipeColors.OnSurface,
            modifier = Modifier.padding(bottom = CardGap),
        )
        SettingsCategory.entries.chunked(CARDS_PER_ROW).forEach { rowCategories ->
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.fillMaxWidth()) {
                rowCategories.forEach { category ->
                    SettingsCard(
                        category = category,
                        onClick = { onOpen(category) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(cardFocusRequesters.getValue(category)),
                    )
                }
                // A short last row keeps the same card width as the full rows above it.
                repeat(CARDS_PER_ROW - rowCategories.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (restoreFocusTo != null) {
        LaunchedEffect(Unit) {
            // The cards attach on the first frame; focusing earlier finds nothing to focus.
            withFrameNanos { }
            cardFocusRequesters.getValue(restoreFocusTo).requestFocus()
        }
    }
}

@Composable
private fun SettingsCard(category: SettingsCategory, onClick: () -> Unit, modifier: Modifier) {
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (hasFocus) FOCUSED_CARD_SCALE else 1f, label = "card scale")
    val shape = RoundedCornerShape(CardCornerRadius)
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .height(CardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (!hasFocus) return@drawBehind
                // The ring sits outside the card: its centre line is offset plus half the stroke from the edge.
                val outset = (FocusRingOffset + FocusRingWidth / 2).toPx()
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(-outset, -outset),
                    size = Size(size.width + outset * 2, size.height + outset * 2),
                    cornerRadius = CornerRadius(CardCornerRadius.toPx() + outset),
                    style = Stroke(width = FocusRingWidth.toPx()),
                )
            }
            .background(Brush.linearGradient(listOf(category.gradientStart, category.gradientEnd)), shape)
            .onFocusChanged { hasFocus = it.hasFocus }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CardPadding),
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(CardIconSize),
        )
        Text(
            text = stringResource(category.titleResource),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
