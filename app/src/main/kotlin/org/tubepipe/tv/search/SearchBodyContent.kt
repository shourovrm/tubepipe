package org.tubepipe.tv.search

/** What the area under the search field shows; decides whether Down from the field has a target. */
internal enum class SearchBodyContent(val hasFocusableItems: Boolean) {
    Suggestions(true),
    RecentSearches(true),
    Prompt(false),

    /** A new query is being typed and its suggestions have not arrived; earlier results must not show. */
    Typing(false),
    Loading(false),
    Error(false),
    NoResults(false),
    Results(true),
}

internal fun bodyContentFor(state: SearchState): SearchBodyContent {
    val isTypingNewQuery = state.query.isNotBlank() && state.query != state.submittedQuery
    return when {
        isTypingNewQuery && state.suggestions.isNotEmpty() -> SearchBodyContent.Suggestions
        state.query.isBlank() && state.recentSearches.isNotEmpty() -> SearchBodyContent.RecentSearches
        // Suggestions arrive after a 300 ms pause; until then the old results would flip the screen to
        // Results, which hides the keyboard mid-typing.
        isTypingNewQuery -> SearchBodyContent.Typing
        state.submittedQuery == null -> SearchBodyContent.Prompt
        state.isLoading -> SearchBodyContent.Loading
        state.hasError -> SearchBodyContent.Error
        state.items.isEmpty() -> SearchBodyContent.NoResults
        else -> SearchBodyContent.Results
    }
}

/**
 * Focus moves to the results once per submission. Showing the same results again (the viewer typed the
 * submitted query back) is not a new submission and must leave the keyboard open.
 */
internal fun shouldMoveFocusToResults(
    bodyContent: SearchBodyContent,
    submissionCount: Int,
    lastFocusedSubmissionCount: Int,
): Boolean = bodyContent == SearchBodyContent.Results && submissionCount != lastFocusedSubmissionCount
