package org.tubepipe.core.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendingCatalogTest {
    @Test
    fun trendingTabsAreMusicMoviesAndGamingInThatOrder() {
        assertEquals(
            listOf(TrendingKind.Music, TrendingKind.MoviesAndShows, TrendingKind.Gaming),
            TrendingKind.entries,
        )
    }

    @Test
    fun musicIsHiddenWhereYoutubeChartsHasNoData() {
        assertEquals(listOf(TrendingKind.MoviesAndShows, TrendingKind.Gaming), TrendingCatalog.kindsFor("BD"))
    }

    @Test
    fun everyTabIsShownWhereYoutubeChartsHasData() {
        assertEquals(TrendingKind.entries, TrendingCatalog.kindsFor("US"))
        assertEquals(TrendingKind.entries, TrendingCatalog.kindsFor("GB"))
    }

    @Test
    fun countryCodesAreCaseInsensitive() {
        assertEquals(TrendingCatalog.kindsFor("US"), TrendingCatalog.kindsFor("us"))
        assertEquals(TrendingCatalog.kindsFor("BD"), TrendingCatalog.kindsFor("bd"))
    }
}
