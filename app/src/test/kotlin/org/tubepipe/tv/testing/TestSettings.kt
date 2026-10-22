package org.tubepipe.tv.testing

import kotlinx.coroutines.flow.MutableStateFlow
import org.tubepipe.tv.data.AppSettings
import org.tubepipe.tv.data.HomeSection

fun testSettings(
    contentCountry: String = "US",
    hiddenHomeSections: Set<HomeSection> = emptySet(),
    homeSectionOrder: List<HomeSection> = HomeSection.entries,
    sponsorBlockEnabled: Boolean = true,
    playerClientName: String = "visionos",
): MutableStateFlow<AppSettings> = MutableStateFlow(
    AppSettings(
        contentCountry = contentCountry,
        playerClientName = playerClientName,
        homeSectionOrder = homeSectionOrder,
        hiddenHomeSections = hiddenHomeSections,
        sponsorBlockEnabled = sponsorBlockEnabled,
    ),
)
