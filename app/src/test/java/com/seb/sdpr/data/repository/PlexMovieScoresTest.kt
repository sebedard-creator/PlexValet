package com.seb.sdpr.data.repository

import com.google.gson.Gson
import com.seb.sdpr.data.remote.PlexMovie
import com.seb.sdpr.data.remote.PlexRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlexMovieScoresTest {
    private val gson = Gson()
    private fun metadata(json: String): PlexMovie = gson.fromJson(json, PlexMovie::class.java)
    private fun scores(json: String) = resolvePlexMovieScores(null, metadata(json))

    @Test
    fun `structured ratings select RT critics and IMDb independently`() {
        val result = scores("""{
            "Rating":[
                {"image":"themoviedb://image.rating","type":"audience","value":8.321},
                {"image":"rottentomatoes://image.rating.upright","type":"audience","value":9.5},
                {"image":"imdb://image.rating","type":"audience","value":8.5},
                {"image":"rottentomatoes://image.rating.ripe","type":"critic","value":9.3}
            ]
        }""")
        assertEquals(9.3f, result.rottenTomatoesCritics)
        assertEquals(8.5f, result.imdb)
    }

    @Test
    fun `legacy source identifiers identify both ratings`() {
        val result = scores("""{
            "rating":9.3,"ratingImage":"rottentomatoes://image.rating.ripe",
            "audienceRating":8.5,"audienceRatingImage":"imdb://image.rating"
        }""")
        assertEquals(9.3f, result.rottenTomatoesCritics)
        assertEquals(8.5f, result.imdb)
    }

    @Test
    fun `IMDb may be the primary rating without becoming an RT score`() {
        val result = scores("""{"rating":7.8,"ratingImage":"imdb://image.rating"}""")
        assertNull(result.rottenTomatoesCritics)
        assertEquals(7.8f, result.imdb)
    }

    @Test
    fun `RT score never becomes a missing IMDb score`() {
        val result = scores("""{"rating":9.3,"ratingImage":"rottentomatoes://image.rating.ripe"}""")
        assertEquals(9.3f, result.rottenTomatoesCritics)
        assertNull(result.imdb)
    }

    @Test
    fun `unidentified generic ratings do not claim either source`() {
        val result = scores("""{"rating":9.3,"audienceRating":8.5}""")
        assertNull(result.rottenTomatoesCritics)
        assertNull(result.imdb)
    }

    @Test
    fun `RT audience is not mistaken for RT critics`() {
        val result = scores("""{
            "rating":9.5,"ratingImage":"rottentomatoes://image.rating.upright",
            "Rating":[{"image":"rottentomatoes://image.rating.upright","type":"audience","value":9.5}]
        }""")
        assertNull(result.rottenTomatoesCritics)
        assertNull(result.imdb)
    }

    @Test
    fun `detail ratings take precedence over list ratings`() {
        val detailed = metadata("""{"Rating":[
            {"image":"rottentomatoes://image.rating.ripe","type":"critic","value":9.3},
            {"image":"imdb://image.rating","type":"audience","value":8.5}
        ]}""")
        val fallback = metadata("""{"Rating":[
            {"image":"rottentomatoes://image.rating.rotten","type":"critic","value":4.0},
            {"image":"imdb://image.rating","type":"audience","value":6.0}
        ]}""")
        val result = resolvePlexMovieScores(detailed, fallback)
        assertEquals(9.3f, result.rottenTomatoesCritics)
        assertEquals(8.5f, result.imdb)
    }

    @Test
    fun `each missing source can fall back to list metadata`() {
        val detailed = metadata("""{"rating":9.3,"ratingImage":"rottentomatoes://image.rating.ripe"}""")
        val fallback = metadata("""{"rating":8.5,"ratingImage":"imdb://image.rating"}""")
        val result = resolvePlexMovieScores(detailed, fallback)
        assertEquals(9.3f, result.rottenTomatoesCritics)
        assertEquals(8.5f, result.imdb)
    }

    @Test
    fun `invalid scores and percent values on a ten point scale are rejected`() {
        for (value in listOf(-1f, 10.1f, 93f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val movie = metadata("{}").copy(ratings = listOf(
                PlexRating("rottentomatoes://image.rating.ripe", "critic", value),
                PlexRating("imdb://image.rating", "audience", value)
            ))
            val result = resolvePlexMovieScores(null, movie)
            assertNull(result.rottenTomatoesCritics)
            assertNull(result.imdb)
        }
    }

    @Test
    fun `zero and ten are valid scores`() {
        val result = scores("""{"Rating":[
            {"image":"rottentomatoes://image.rating.rotten","type":"critic","value":0},
            {"image":"imdb://image.rating","type":"audience","value":10}
        ]}""")
        assertEquals(0f, result.rottenTomatoesCritics)
        assertEquals(10f, result.imdb)
    }

    @Test
    fun `two genuine sources may coincidentally have equal values`() {
        val result = scores("""{"Rating":[
            {"image":"rottentomatoes://image.rating.ripe","type":"critic","value":8},
            {"image":"imdb://image.rating","type":"audience","value":8}
        ]}""")
        assertEquals(8f, result.rottenTomatoesCritics)
        assertEquals(8f, result.imdb)
    }

    @Test
    fun `source names must match rather than merely contain a known name`() {
        val result = scores("""{"Rating":[
            {"image":"notrottentomatoes://image.rating.ripe","type":"critic","value":9},
            {"image":"notimdb://image.rating","type":"audience","value":8}
        ]}""")
        assertNull(result.rottenTomatoesCritics)
        assertNull(result.imdb)
    }
}
