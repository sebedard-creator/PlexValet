package com.seb.sdpr.data.repository

import com.seb.sdpr.data.remote.PlexMovie
import java.util.Locale

internal data class PlexMovieScores(
    val rottenTomatoesCritics: Float?,
    val imdb: Float?
)

internal fun resolvePlexMovieScores(detailedMovie: PlexMovie?, fallbackMovie: PlexMovie): PlexMovieScores {
    val metadata = listOfNotNull(detailedMovie, fallbackMovie)
    return PlexMovieScores(
        rottenTomatoesCritics = metadata.firstNotNullOfOrNull { movie ->
            movie.ratings.orEmpty().firstNotNullOfOrNull { rating ->
                rating.value.validScore().takeIf {
                    rating.image.provider() == "rottentomatoes" &&
                        rating.type.equals("critic", ignoreCase = true)
                }
            } ?: movie.rating.validScore().takeIf {
                movie.ratingImage?.trim()?.lowercase(Locale.ROOT) in RT_CRITIC_IMAGES
            }
        },
        imdb = metadata.firstNotNullOfOrNull { movie ->
            movie.ratings.orEmpty().firstNotNullOfOrNull { rating ->
                rating.value.validScore().takeIf { rating.image.provider() == "imdb" }
            } ?: movie.rating.validScore().takeIf { movie.ratingImage.provider() == "imdb" }
                ?: movie.audienceRating.validScore().takeIf { movie.audienceRatingImage.provider() == "imdb" }
        }
    )
}

private fun Float?.validScore(): Float? = this?.takeIf { it.isFinite() && it in 0f..10f }

private fun String?.provider(): String? = this?.trim()?.takeIf { "://" in it }
    ?.substringBefore("://")?.lowercase(Locale.ROOT)

private val RT_CRITIC_IMAGES = setOf(
    "rottentomatoes://image.rating.ripe",
    "rottentomatoes://image.rating.rotten"
)
