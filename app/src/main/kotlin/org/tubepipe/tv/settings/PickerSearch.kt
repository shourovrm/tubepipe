package org.tubepipe.tv.settings

import java.text.Normalizer
import java.util.Locale

private val CombiningMarks = Regex("\\p{Mn}+")

/**
 * The options whose label holds [query], for the type-to-filter pickers. Labels that start with the query come
 * first, then labels that contain it, each group in list order. An option whose value equals the query (a country
 * code such as "gb") counts as starting with it. Case and accents are ignored, since the TV keyboard makes "ô" slow.
 */
internal fun <T> matchingOptions(options: List<PickerOption<T>>, query: String): List<PickerOption<T>> {
    val needle = foldForSearch(query.trim())
    if (needle.isEmpty()) {
        return options
    }
    val startingWithQuery = mutableListOf<PickerOption<T>>()
    val containingQuery = mutableListOf<PickerOption<T>>()
    for (option in options) {
        val label = foldForSearch(option.label)
        val isValueMatch = foldForSearch(option.value.toString()) == needle
        if (label.startsWith(needle) || isValueMatch) {
            startingWithQuery += option
        } else if (label.contains(needle)) {
            containingQuery += option
        }
    }
    return startingWithQuery + containingQuery
}

private fun foldForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    return CombiningMarks.replace(decomposed, "").lowercase(Locale.ROOT)
}
