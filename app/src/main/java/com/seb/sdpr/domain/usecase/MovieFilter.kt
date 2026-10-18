package com.seb.sdpr.domain.usecase

import com.seb.sdpr.domain.model.Movie
import com.seb.sdpr.ui.filter.FilterState
import com.seb.sdpr.ui.filter.WatchedFilter
import com.seb.sdpr.util.normalizeLanguageCode

internal fun filterMovies(
    movies: List<Movie>,
    filter: FilterState,
    ignoreCollectionFilter: Boolean = false
): List<Movie> = movies.filter { movie ->
    val genreOk = filter.selectedGenre == null ||
        movie.genres.any { it.equals(filter.selectedGenre, ignoreCase = true) }

    val collectionOk = ignoreCollectionFilter || filter.selectedCollection == null ||
        movie.collections.any { it.equals(filter.selectedCollection, ignoreCase = true) }

    val ratingOk = filter.selectedContentRatings.isEmpty() ||
        filter.selectedContentRatings.any { selected ->
            movie.contentRating.equals(selected, ignoreCase = true)
        }

    val decadeOk = filter.selectedDecade == null || movie.decade == filter.selectedDecade

    val isDefaultRtRange = filter.rtScoreMin <= 0 && filter.rtScoreMax >= 100
    val rtOk = if (isDefaultRtRange) {
        true
    } else {
        movie.rottenTomatoesRating?.let { rating ->
            val pct = rating * 10f
            pct >= filter.rtScoreMin && pct <= filter.rtScoreMax
        } ?: false
    }

    val langOk = filter.selectedLanguage == null ||
        movie.audioLanguages.ifEmpty { listOf("fr") }.any { lang ->
            normalizeLanguageCode(lang) == normalizeLanguageCode(filter.selectedLanguage)
        }

    val durOk = filter.maxDurationMinutes >= 240 ||
        movie.durationMs == null ||
        (movie.durationMs / 60000) <= filter.maxDurationMinutes

    val resOk = filter.selectedResolution == null ||
        movie.resolution.equals(filter.selectedResolution, ignoreCase = true)

    val watchedOk = when (filter.watchedFilter) {
        WatchedFilter.ALL -> true
        WatchedFilter.UNWATCHED -> movie.viewCount == 0
        WatchedFilter.WATCHED -> movie.viewCount > 0
    }

    genreOk && collectionOk && ratingOk && decadeOk && rtOk && langOk && durOk && resOk && watchedOk
}
