package org.tubepipe.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tubepipe.core.database.HistoryDao
import org.tubepipe.core.database.HistoryEntity
import org.tubepipe.core.database.SubscriptionDao
import org.tubepipe.core.service.ContentRepository
import org.tubepipe.core.service.TrendingCatalog
import org.tubepipe.core.service.TrendingKind
import org.tubepipe.core.service.VideoItem
import org.tubepipe.core.ui.component.TimeFormatting
import org.tubepipe.tv.common.toVideoItem
import org.tubepipe.tv.data.AppSettings
import org.tubepipe.tv.data.HomeSection

data class HomeVideo(val video: VideoItem, val watchedFraction: Float = 0f)

/** One shelf on Home. [seedTitle] is the video a "Mix" row grew from. */
data class HomeRow(val section: HomeSection, val seedTitle: String?, val items: List<HomeVideo>)

data class HomeState(
    val rows: List<HomeRow> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    /** True while [rows] already shows the sections that are ready and slower ones are still loading. */
    val isLoadingMoreSections: Boolean = false,
)

/** The rows Home last showed. Held by the app container, which outlives the view model. */
class HomeRowsMemory {
    var rows: List<HomeRow> = emptyList()
}

class HomeViewModel(
    private val content: ContentRepository,
    private val history: HistoryDao,
    private val subscriptions: SubscriptionDao,
    settings: Flow<AppSettings>,
    private val recommendationStore: RecommendationStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val rowsMemory: HomeRowsMemory = HomeRowsMemory(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        HomeState(rows = rowsMemory.rows, isLoading = rowsMemory.rows.isEmpty()),
    )
    val state: StateFlow<HomeState> = mutableState.asStateFlow()

    private val refreshCount = MutableStateFlow(0)

    // Network results by source, so a history write (every saved playback position) rebuilds the
    // rows from memory instead of asking YouTube again. refresh() empties it.
    private val sourceCache = mutableMapOf<String, List<VideoItem>>()

    /** Everything a Home load depends on; a new value starts a new load. */
    private data class LoadInputs(
        val contentCountry: String,
        val sections: List<HomeSection>,
        val recentHistory: List<HistoryEntity>,
        val subscribedChannelUrls: List<String>,
        val refreshCount: Int,
    )

    /** What one section produced: its rows, and whether any of its sources failed. */
    private class SectionOutcome(val rows: List<HomeRow>, val hadFailure: Boolean)

    init {
        val relevantSettings = settings
            .map { it.contentCountry to it.visibleHomeSections }
            .distinctUntilChanged()
        val subscribedUrls = subscriptions.observeAll().map { channels -> channels.map { it.url } }
        viewModelScope.launch {
            combine(
                relevantSettings,
                history.observeRecent(HISTORY_LIMIT),
                subscribedUrls,
                refreshCount,
            ) { (country, sections), recentHistory, channelUrls, refreshes ->
                LoadInputs(country, sections, recentHistory, channelUrls, refreshes)
            }.collectLatest { inputs -> load(inputs) }
        }
    }

    fun refresh() {
        sourceCache.clear()
        refreshCount.update { it + 1 }
    }

    private suspend fun load(inputs: LoadInputs) {
        // Keep showing the old rows while reloading; a spinner only replaces an empty screen.
        mutableState.update { current -> if (current.rows.isEmpty()) HomeState(isLoading = true) else current }
        showSavedRecommendations(inputs)
        // One slow source (a Mix lookup can take many seconds) must not hold back the rows that are ready, so each
        // section is shown as it arrives. Until then a section keeps whatever rows it had on screen.
        val rowsShownBefore = mutableState.value.rows
        val outcomes = arrayOfNulls<SectionOutcome>(inputs.sections.size)
        coroutineScope {
            inputs.sections.forEachIndexed { index, section ->
                launch {
                    outcomes[index] = loadSection(section, inputs)
                    if (outcomes.any { it == null }) {
                        mutableState.value = HomeState(
                            rows = rowsSoFar(inputs.sections, outcomes, rowsShownBefore),
                            isLoading = false,
                            isLoadingMoreSections = true,
                        )
                    }
                }
            }
        }
        val rows = outcomes.flatMap { it?.rows.orEmpty() }
        val anySourceFailed = outcomes.any { it?.hadFailure == true }
        mutableState.value = HomeState(rows = rows, isLoading = false, hasError = rows.isEmpty() && anySourceFailed)
        if (rows.isNotEmpty()) {
            rowsMemory.rows = rows
        }
    }

    private fun rowsSoFar(
        sections: List<HomeSection>,
        outcomes: Array<SectionOutcome?>,
        rowsShownBefore: List<HomeRow>,
    ): List<HomeRow> = sections.flatMapIndexed { index, section ->
        outcomes[index]?.rows ?: rowsShownBefore.filter { it.section == section }
    }

    /**
     * Shows the Recommended row saved by the last run while the network loads, so Home is not
     * a spinner on every start. Skipped once any rows are on screen; they are already newer.
     */
    private suspend fun showSavedRecommendations(inputs: LoadInputs) {
        if (HomeSection.Recommended !in inputs.sections || mutableState.value.rows.isNotEmpty()) {
            return
        }
        val savedVideos = recommendationStore.load()
        if (savedVideos.isEmpty()) {
            return
        }
        val savedRow = HomeRow(HomeSection.Recommended, seedTitle = null, items = savedVideos.asHomeVideos())
        mutableState.update { current ->
            if (current.rows.isEmpty()) HomeState(rows = listOf(savedRow), isLoading = false) else current
        }
    }

    private suspend fun loadSection(section: HomeSection, inputs: LoadInputs): SectionOutcome = when (section) {
        HomeSection.ContinueWatching -> SectionOutcome(continueWatchingRows(inputs.recentHistory), hadFailure = false)
        HomeSection.Recommended -> recommendedRows(inputs.recentHistory)
        HomeSection.Mix -> mixRows(inputs.recentHistory)
        HomeSection.NewFromSubscriptions -> subscriptionFeedRows(inputs.subscribedChannelUrls)
        HomeSection.TrendingMusic -> trendingRows(TrendingKind.Music, section, inputs.contentCountry)
        HomeSection.TrendingMovies -> trendingRows(TrendingKind.MoviesAndShows, section, inputs.contentCountry)
    }

    private fun continueWatchingRows(recentHistory: List<HistoryEntity>): List<HomeRow> {
        val partlyWatched = recentHistory.mapNotNull { entry ->
            val fraction = TimeFormatting.watchedFraction(entry.positionMillis, entry.durationSeconds)
            if (fraction > 0f && fraction < FINISHED_FRACTION) {
                HomeVideo(entry.toVideoItem(), fraction)
            } else {
                null
            }
        }
        return rowIfNotEmpty(HomeSection.ContinueWatching, seedTitle = null, items = partlyWatched)
    }

    private suspend fun recommendedRows(recentHistory: List<HistoryEntity>): SectionOutcome = coroutineScope {
        val seeds = recommendationSeeds(recentHistory)
        val relatedPerSeed = seeds.map { seed ->
            async { fetchSource("related:${seed.url}") { content.videoDetails(seed.url).related } }
        }.awaitAll()
        val alreadyWatchedUrls = recentHistory.map { entry -> entry.url }.toSet()
        val recommended = relatedPerSeed
            .filterNotNull()
            .flatten()
            .distinctBy { video -> video.url }
            .filter { video -> video.url !in alreadyWatchedUrls }
            .shuffled(random)
            .take(RECOMMENDED_LIMIT)
        if (recommended.isNotEmpty()) {
            recommendationStore.save(recommended)
        }
        SectionOutcome(
            rows = rowIfNotEmpty(HomeSection.Recommended, seedTitle = null, items = recommended.asHomeVideos()),
            hadFailure = relatedPerSeed.any { related -> related == null },
        )
    }

    /** History videos from the last few days; when there are none, the newest few, so a lapsed viewer still gets a row. */
    private fun recommendationSeeds(recentHistory: List<HistoryEntity>): List<HistoryEntity> {
        val oldestSeedMillis = clock() - RECOMMENDED_WINDOW_MILLIS
        val watchedLately = recentHistory.filter { entry -> entry.lastWatchedAtMillis >= oldestSeedMillis }
        val seeds = watchedLately.ifEmpty { recentHistory.take(RECOMMENDED_FALLBACK_SEED_COUNT) }
        return seeds.take(RECOMMENDED_SEED_LIMIT)
    }

    private suspend fun mixRows(recentHistory: List<HistoryEntity>): SectionOutcome {
        val newest = recentHistory.firstOrNull() ?: return SectionOutcome(emptyList(), hadFailure = false)
        val mix = fetchSource("mix:${newest.url}") { content.mix(newest.url) }
        return outcomeOf(listOf(newest to mix), HomeSection.Mix)
    }

    private suspend fun subscriptionFeedRows(channelUrls: List<String>): SectionOutcome {
        if (channelUrls.isEmpty()) {
            return SectionOutcome(emptyList(), hadFailure = false)
        }
        val feed = fetchSource("feed:${channelUrls.sorted().joinToString(",")}") {
            content.latestFromChannels(channelUrls)
        }
        return SectionOutcome(
            rows = rowIfNotEmpty(HomeSection.NewFromSubscriptions, null, feed.orEmpty().asHomeVideos()),
            hadFailure = feed == null,
        )
    }

    private suspend fun trendingRows(kind: TrendingKind, section: HomeSection, country: String): SectionOutcome {
        if (kind !in TrendingCatalog.kindsFor(country)) {
            return SectionOutcome(emptyList(), hadFailure = false)
        }
        val videos = fetchSource("trending:$kind:$country") { content.trending(kind) }
        return SectionOutcome(
            rows = rowIfNotEmpty(section, null, videos.orEmpty().asHomeVideos()),
            hadFailure = videos == null,
        )
    }

    /** Pairs each seed history entry with its fetched videos; a null list means that fetch failed. */
    private fun outcomeOf(
        seedsWithVideos: List<Pair<HistoryEntity, List<VideoItem>?>>,
        section: HomeSection,
    ): SectionOutcome {
        val rows = seedsWithVideos.flatMap { (seed, videos) ->
            rowIfNotEmpty(section, seed.title, videos.orEmpty().asHomeVideos())
        }
        return SectionOutcome(rows, hadFailure = seedsWithVideos.any { (_, videos) -> videos == null })
    }

    private fun rowIfNotEmpty(section: HomeSection, seedTitle: String?, items: List<HomeVideo>): List<HomeRow> =
        if (items.isEmpty()) emptyList() else listOf(HomeRow(section, seedTitle, items))

    private fun List<VideoItem>.asHomeVideos(): List<HomeVideo> = map { HomeVideo(it) }

    /**
     * Returns the cached or freshly fetched videos, or null when the fetch failed. The repository
     * reports every YouTube failure as IOException; a failed source is logged and its row left out.
     */
    private suspend fun fetchSource(cacheKey: String, fetch: suspend () -> List<VideoItem>): List<VideoItem>? {
        sourceCache[cacheKey]?.let { return it }
        return try {
            fetch().also { sourceCache[cacheKey] = it }
        } catch (failure: IOException) {
            logFailure(cacheKey, failure)
        }
    }

    private fun logFailure(cacheKey: String, failure: IOException): List<VideoItem>? {
        logger.log(Level.WARNING, "Home source $cacheKey failed; its row is left out", failure)
        return null
    }

    private companion object {
        const val HISTORY_LIMIT = 50
        const val RECOMMENDED_WINDOW_MILLIS = 3 * 24 * 60 * 60 * 1_000L
        const val RECOMMENDED_FALLBACK_SEED_COUNT = 3
        const val RECOMMENDED_SEED_LIMIT = 5
        const val RECOMMENDED_LIMIT = 30
        const val FINISHED_FRACTION = 0.95f
        val logger: Logger = Logger.getLogger(HomeViewModel::class.java.name)
    }
}
