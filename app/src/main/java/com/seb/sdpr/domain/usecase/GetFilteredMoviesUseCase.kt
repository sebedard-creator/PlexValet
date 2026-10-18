package com.seb.sdpr.domain.usecase

import com.seb.sdpr.data.repository.MovieRepository
import com.seb.sdpr.domain.model.Movie
import com.seb.sdpr.ui.filter.FilterState
import javax.inject.Inject

class GetFilteredMoviesUseCase @Inject constructor(
    private val repository: MovieRepository
) {
    suspend operator fun invoke(
        filter: FilterState,
        sourceMovies: List<Movie>? = null,
        ignoreCollectionFilter: Boolean = false
    ): List<Movie> {
        val movies = sourceMovies ?: repository.getAllMoviesOnce()
        return filterMovies(
            movies = movies,
            filter = filter,
            ignoreCollectionFilter = ignoreCollectionFilter
        ).sortedWith(
            compareBy<Movie> { it.title.lowercase() }
                .thenBy { it.year ?: Int.MAX_VALUE }
        )
    }
}
