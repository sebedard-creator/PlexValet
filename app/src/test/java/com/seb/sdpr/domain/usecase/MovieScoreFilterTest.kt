package com.seb.sdpr.domain.usecase

import com.google.gson.Gson
import com.seb.sdpr.domain.model.Movie
import com.seb.sdpr.ui.filter.FilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieScoreFilterTest {
    private val gson = Gson()
    private fun movie(rt: Float?, imdb: Float?): Movie = gson.fromJson("""{
        "ratingKey":"1","title":"Film","genres":[],"collections":[],
        "audioLanguages":[],"subtitleLanguages":[],"viewCount":0,"lastSyncedAt":0,
        "rating":9.9,"audienceRating":9.8,
        "rottenTomatoesRating":$rt,"imdbRating":$imdb
    }""", Movie::class.java)

    @Test
    fun `RT filter uses RT rather than IMDb or a generic rating`() {
        assertTrue(filterMovies(listOf(movie(2f, 9f)), FilterState(rtScoreMin = 80)).isEmpty())
        assertEquals(1, filterMovies(listOf(movie(9f, 2f)), FilterState(rtScoreMin = 80)).size)
    }

    @Test
    fun `old cache without verified sources is not treated as RT`() {
        val oldMovie = movie(null, null)
        assertTrue(filterMovies(listOf(oldMovie), FilterState(rtScoreMin = 80)).isEmpty())
        assertEquals(null, oldMovie.rottenTomatoesPercent)
    }

    @Test
    fun `unfiltered movies remain available without ratings`() {
        assertEquals(1, filterMovies(listOf(movie(null, null)), FilterState()).size)
    }

    @Test
    fun `RT display rounds the percent without borrowing the IMDb score`() {
        assertEquals(93, movie(9.3f, 8.5f).rottenTomatoesPercent)
        assertEquals(100, movie(10f, 0f).rottenTomatoesPercent)
        assertEquals(0, movie(0f, 10f).rottenTomatoesPercent)
    }
}
