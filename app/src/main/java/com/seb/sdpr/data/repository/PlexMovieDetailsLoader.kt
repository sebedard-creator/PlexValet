package com.seb.sdpr.data.repository

import com.seb.sdpr.data.remote.PlexMovie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class PlexMovieDetailsLoader {
    // Shared across pages and calls so that this loader never exceeds the limit.
    private val semaphore = Semaphore(MAX_CONCURRENT_DETAILS)

    suspend fun load(
        movies: List<PlexMovie>,
        onProgress: (Int) -> Unit = {},
        fetchDetails: suspend (String) -> PlexMovie?
    ): List<Pair<PlexMovie, PlexMovie?>> = coroutineScope {
        val loadContext = coroutineContext
        val progressLock = Any()
        var completed = 0
        movies.map { movie ->
            async(Dispatchers.IO) {
                val ratingKey = checkNotNull(movie.ratingKey)
                val details = semaphore.withPermit {
                    // Parent cancellation reaches children one by one; don't start queued calls.
                    loadContext.ensureActive()
                    try {
                        fetchDetails(ratingKey)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the movie's list metadata if its detail request fails.
                        null
                    }
                }
                // Requests finish on different IO threads; publish counts in order.
                synchronized(progressLock) {
                    loadContext.ensureActive()
                    onProgress(++completed)
                }
                movie to details
            }
        }.awaitAll()
    }

    companion object {
        const val MAX_CONCURRENT_DETAILS = 4
    }
}
