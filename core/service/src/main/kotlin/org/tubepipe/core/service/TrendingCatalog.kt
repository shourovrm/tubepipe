package org.tubepipe.core.service

import java.util.Locale
import org.tubepipe.core.service.kiosk.YoutubeChartsBaseKioskExtractor

/** The trending tabs, in the order the UI shows them. */
enum class TrendingKind { Music, MoviesAndShows, Gaming }

object TrendingCatalog {
    /** YouTube Charts feeds the Music tab, and it is not offered in every country (not in BD). */
    fun kindsFor(countryCode: String): List<TrendingKind> {
        val chartsAvailable = YoutubeChartsBaseKioskExtractor.YT_CHARTS_SUPPORTED_COUNTRY_CODES
            .contains(countryCode.uppercase(Locale.ROOT))
        return if (chartsAvailable) {
            TrendingKind.entries
        } else {
            TrendingKind.entries - TrendingKind.Music
        }
    }
}
