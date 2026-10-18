package com.seb.sdpr.data.repository

import com.google.gson.Gson
import com.seb.sdpr.data.local.MovieDao
import com.seb.sdpr.data.local.MovieEntity
import com.seb.sdpr.data.remote.*
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlexIncrementalSyncTest {
    private val gson = Gson()
    private val cache = PlexMovieSyncCache("server-id")

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java)
    ) { _, method, _ -> error("Unexpected call: ${method.name}") } as T

    private fun movie(key: String, updatedAt: Long? = 100) =
        gson.fromJson("""{"ratingKey":"$key","title":"Film $key","updatedAt":$updatedAt}""", PlexMovie::class.java)

    private fun cached(movie: PlexMovie): MovieEntity = gson.fromJson(
        """{"ratingKey":"${movie.ratingKey}","title":"${movie.title}","genres":"[]","collections":"[\"Old collection\"]","audioLanguages":"[\"en\"]","subtitleLanguages":"[]","viewCount":1,"lastSyncedAt":1}""",
        MovieEntity::class.java
    ).copy(syncFingerprint = cache.fingerprint("1", movie))

    private inner class Api(val movies: List<PlexMovie>) : PlexApi by unused<PlexApi>() {
        val detailCalls = ConcurrentLinkedQueue<String>()
        var failDetails = false
        var failCollections = false
        override suspend fun getServerInfo(url: String, token: String) =
            PlexServerResponse(ServerContainer("server-id", "Server"))
        override suspend fun getSections(url: String, token: String) =
            PlexSectionsResponse(SectionsContainer(listOf(PlexSection("1", "Films", "movie"))))
        override suspend fun getMovies(url: String, token: String): PlexMoviesResponse {
            if ("/sections/" in url) return PlexMoviesResponse(MoviesContainer(movies.size, movies.size, movies))
            val key = url.substringAfterLast('/')
            detailCalls.add(key)
            if (failDetails) throw IOException("Unavailable details")
            return PlexMoviesResponse(MoviesContainer(1, 1, listOf(movies.first { it.ratingKey == key })))
        }
        override suspend fun getCollections(url: String, token: String): PlexCollectionsResponse {
            if (failCollections) throw IOException("Unavailable collections")
            return PlexCollectionsResponse(CollectionsContainer(0, 0, emptyList()))
        }
    }

    private inner class Dao(val existing: List<MovieEntity>) : MovieDao by unused<MovieDao>() {
        var attempted: List<MovieEntity>? = null
        val stopAtWrite = IOException("Simulated database write failure")
        override suspend fun getAllMoviesOnce() = existing
        override suspend fun replaceAll(movies: List<MovieEntity>) {
            attempted = movies
            // Stop before Android logging; also exercise the transactional failure path.
            throw stopAtWrite
        }
    }

    @Test
    fun `unchanged details are reused while watch counts and collection removals stay current`() = runBlocking {
        val original = movie("a")
        val api = Api(listOf(original.copy(viewCount = 4)))
        val dao = Dao(listOf(cached(original)))
        val result = MovieRepository(api, dao).syncLibrary("server", "token")
        assertEquals(dao.stopAtWrite.javaClass, result.exceptionOrNull()?.javaClass)
        assertEquals(dao.stopAtWrite.message, result.exceptionOrNull()?.message)
        assertTrue(api.detailCalls.isEmpty())
        val saved = dao.attempted!!.single()
        assertEquals(4, saved.viewCount)
        assertEquals("[\"en\"]", saved.audioLanguages)
        assertEquals("[]", saved.collections)
        assertTrue(saved.lastSyncedAt > 1)
    }

    @Test
    fun `only new and modified movies request details and deleted movies disappear`() = runBlocking {
        val a = movie("a")
        val b = movie("b")
        val api = Api(listOf(a, b, movie("c")))
        val dao = Dao(listOf(cached(a), cached(b.copy(updatedAt = 99)), cached(movie("deleted"))))
        MovieRepository(api, dao).syncLibrary("server", "token")
        assertEquals(setOf("b", "c"), api.detailCalls.toSet())
        assertEquals(listOf("a", "b", "c"), dao.attempted!!.map { it.ratingKey })
        assertTrue(dao.attempted!!.all { it.syncFingerprint != null })
    }

    @Test
    fun `forced refresh reads every detail even when the cache matches`() = runBlocking {
        val movie = movie("a")
        val api = Api(listOf(movie))
        val dao = Dao(listOf(cached(movie)))
        MovieRepository(api, dao).syncLibrary("server", "token", forceFull = true)
        assertEquals(listOf("a"), api.detailCalls.toList())
        assertNotNull(dao.attempted!!.single().syncFingerprint)
    }

    @Test
    fun `failed details do not become reusable cache entries`() = runBlocking {
        val api = Api(listOf(movie("a"))).apply { failDetails = true }
        val dao = Dao(emptyList())
        MovieRepository(api, dao).syncLibrary("server", "token")
        assertEquals(listOf("a"), api.detailCalls.toList())
        assertNull(dao.attempted!!.single().syncFingerprint)
    }

    @Test
    fun `missing modification marker falls back to a full detail request`() = runBlocking {
        val movie = movie("a", updatedAt = null)
        val api = Api(listOf(movie))
        val dao = Dao(listOf(cached(movie)))
        MovieRepository(api, dao).syncLibrary("server", "token")
        assertEquals(listOf("a"), api.detailCalls.toList())
        assertNull(dao.attempted!!.single().syncFingerprint)
    }

    @Test
    fun `unavailable collections preserve previous memberships`() = runBlocking {
        val movie = movie("a")
        val api = Api(listOf(movie)).apply { failCollections = true }
        val dao = Dao(listOf(cached(movie)))
        MovieRepository(api, dao).syncLibrary("server", "token")
        assertEquals("[\"Old collection\"]", dao.attempted!!.single().collections)
    }

    @Test
    fun `server section timestamps and media changes invalidate the fingerprint`() {
        val movie = movie("a")
        val expected = cache.fingerprint("1", movie)
        assertNotNull(expected)
        assertEquals(expected, cache.fingerprint("1", movie.copy(viewCount = 20)))
        assertNotEquals(expected, cache.fingerprint("1", movie.copy(updatedAt = 101)))
        assertNotEquals(expected, cache.fingerprint("2", movie))
        assertNotEquals(expected, PlexMovieSyncCache("another-server").fingerprint("1", movie))
        val withFile = gson.fromJson(
            """{"ratingKey":"a","title":"Film a","updatedAt":100,"Media":[{"id":"2","Part":[{"id":"3","key":"/library/parts/3","size":1000}]}]}""",
            PlexMovie::class.java
        )
        assertNotEquals(expected, cache.fingerprint("1", withFile))
        val replaced = withFile.copy(media = withFile.media!!.map { media ->
            media.copy(parts = media.parts!!.map { it.copy(id = "4", size = 2000) })
        })
        assertNotEquals(cache.fingerprint("1", withFile), cache.fingerprint("1", replaced))
        assertNull(PlexMovieSyncCache(null).fingerprint("1", movie))
        assertNull(cache.fingerprint("1", movie.copy(updatedAt = null)))
    }
}
