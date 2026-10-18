package com.seb.sdpr.data.repository

import com.google.gson.Gson
import com.seb.sdpr.data.local.MovieDao
import com.seb.sdpr.data.local.MovieEntity
import com.seb.sdpr.data.remote.*
import com.seb.sdpr.domain.model.LibrarySyncProgress
import com.seb.sdpr.domain.model.LibrarySyncStage
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibrarySyncProgressTest {
    private val gson = Gson()
    private val emptyDao = object : MovieDao by unused<MovieDao>() {
        override suspend fun getAllMoviesOnce() = emptyList<MovieEntity>()
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java)
    ) { _, method, _ -> error("Unexpected call: ${method.name}") } as T

    private fun movie(key: String): PlexMovie =
        gson.fromJson("""{"ratingKey":"$key","title":"Film $key"}""", PlexMovie::class.java)

    private fun sections() = PlexSectionsResponse(SectionsContainer(listOf(
        PlexSection("1", "Films A", "movie"), PlexSection("2", "Films B", "movie")
    )))

    @Test
    fun `movie counters span pages and reset explicitly for the next section`() = runBlocking {
        val api = object : PlexApi by unused<PlexApi>() {
            override suspend fun getSections(url: String, token: String) = sections()
            override suspend fun getMovies(url: String, token: String): PlexMoviesResponse {
                val page = when {
                    "/sections/1/" in url && "Start=0&" in url -> MoviesContainer(2, 3, listOf(movie("a"), movie("b")))
                    "/sections/1/" in url -> MoviesContainer(1, 3, listOf(movie("c")))
                    "/sections/2/" in url -> MoviesContainer(1, 1, listOf(movie("d")))
                    else -> MoviesContainer(1, null, listOf(movie(url.substringAfterLast('/'))))
                }
                return PlexMoviesResponse(page)
            }
            override suspend fun getCollections(url: String, token: String) =
                PlexCollectionsResponse(CollectionsContainer(0, 0, emptyList()))
        }
        val updates = mutableListOf<LibrarySyncProgress>()
        val stopBeforeSaving = IllegalStateException("Stop at the database boundary")
        val result = MovieRepository(api, emptyDao).syncLibrary("server", "token") {
            updates.add(it)
            // Progress tests do not write to Room or call the Android logger.
            if (it.stage == LibrarySyncStage.SAVING) throw stopBeforeSaving
        }
        assertEquals(stopBeforeSaving.javaClass, result.exceptionOrNull()?.javaClass)
        assertEquals(stopBeforeSaving.message, result.exceptionOrNull()?.message)
        val films = updates.filter { it.stage == LibrarySyncStage.MOVIES && it.total != null }
        val first = films.filter { it.sectionIndex == 1 }
        assertEquals(listOf(0, 1, 2, 2, 2, 3), first.map { it.completed })
        assertTrue(first.all { it.total == 3 && it.sectionCount == 2 })
        val second = films.filter { it.sectionIndex == 2 }
        assertEquals(listOf(0, 1), second.map { it.completed })
        assertTrue(second.all { it.total == 1 && it.sectionCount == 2 })
        assertEquals(LibrarySyncStage.CONNECTING, updates.first().stage)
        assertEquals(LibrarySyncStage.SAVING, updates.last().stage)
    }

    @Test
    fun `catalog counts include invalid entries and reach the total in each section`() = runBlocking {
        val api = object : PlexApi by unused<PlexApi>() {
            override suspend fun getSections(url: String, token: String) = sections()
            override suspend fun getCollections(url: String, token: String) =
                if ("/sections/" in url) PlexCollectionsResponse(CollectionsContainer(2, 2, listOf(
                    PlexCollection("10", null, "Collection", false), PlexCollection(null, null, "Invalid", false)
                ))) else PlexCollectionsResponse(CollectionsContainer(0, 0, emptyList()))
        }
        val updates = mutableListOf<LibrarySyncProgress>()
        MovieRepository(api, unused<MovieDao>()).fetchCollectionCatalog("server", "token", updates::add)
        for (section in 1..2) {
            assertEquals(listOf(0, 1, 2), updates.filter {
                it.sectionIndex == section && it.total == 2
            }.map { it.completed })
        }
        assertTrue(updates.all { it.stage == LibrarySyncStage.CATALOG })
    }

    @Test
    fun `cancelling catalog work does not continue or report a false completion`() = runBlocking {
        val api = object : PlexApi by unused<PlexApi>() {
            override suspend fun getSections(url: String, token: String) = sections()
            override suspend fun getCollections(url: String, token: String): PlexCollectionsResponse {
                throw CancellationException("Cancelled")
            }
        }
        val updates = mutableListOf<LibrarySyncProgress>()
        try {
            MovieRepository(api, unused<MovieDao>()).fetchCollectionCatalog("server", "token", updates::add)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(updates.all { it.total == null && it.completed == 0 })
        }
    }
}
