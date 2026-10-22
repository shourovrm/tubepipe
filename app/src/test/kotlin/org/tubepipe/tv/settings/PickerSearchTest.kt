package org.tubepipe.tv.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PickerSearchTest {
    private val countries = listOf(
        PickerOption("BH", "Bahrain"),
        PickerOption("BD", "Bangladesh"),
        PickerOption("CI", "Côte d’Ivoire"),
        PickerOption("GB", "United Kingdom"),
        PickerOption("UA", "Ukraine"),
    )

    private fun labels(query: String) = matchingOptions(countries, query).map { it.label }

    @Test
    fun anEmptyQueryKeepsEveryOptionInOrder() {
        assertEquals(countries.map { it.label }, labels("  "))
    }

    @Test
    fun matchingIgnoresCase() {
        assertEquals(listOf("Bangladesh"), labels("BANG"))
    }

    @Test
    fun namesThatStartWithTheQueryComeBeforeNamesThatContainIt() {
        // "ba" starts Bahrain and Bangladesh and sits inside no other name.
        assertEquals(listOf("Bahrain", "Bangladesh"), labels("ba"))
        // "u" starts Ukraine and United Kingdom; "Côte d’Ivoire" has no u.
        assertEquals(listOf("United Kingdom", "Ukraine"), labels("u"))
        // "in" starts no name, so the contains matches keep list order.
        assertEquals(listOf("Bahrain", "United Kingdom", "Ukraine"), labels("in"))
    }

    @Test
    fun accentsDoNotHaveToBeTyped() {
        assertEquals(listOf("Côte d’Ivoire"), labels("cote"))
    }

    @Test
    fun theCountryCodeAlsoMatches() {
        assertEquals(listOf("United Kingdom"), labels("gb"))
    }
}
