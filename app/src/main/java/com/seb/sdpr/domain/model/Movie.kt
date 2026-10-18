package com.seb.sdpr.domain.model

import kotlin.math.roundToInt

data class Movie(
    val ratingKey: String,
    val title: String,
    val year: Int?,
    val durationMs: Long?,
    val director: String?,
    val contentRating: String?,
    val genres: List<String>,
    val collections: List<String>,
    val rating: Float?,
    val audienceRating: Float?,
    val summary: String?,
    val thumbPath: String?,
    val audioLanguages: List<String>,
    val subtitleLanguages: List<String>,
    val resolution: String?,
    val hdrFormat: String?,
    val bitrateKbps: Int?,
    val viewCount: Int,
    val lastSyncedAt: Long,
    val rottenTomatoesRating: Float? = null,
    val imdbRating: Float? = null
) {
    val durationMinutes: Int? get() = durationMs?.let { (it / 60000).toInt() }
    val decade: String? get() = year?.let { "${(it / 10) * 10}s" }
    val rottenTomatoesPercent: Int? get() = rottenTomatoesRating?.let { (it * 10).roundToInt() }
    val bitrateMbpsDisplay: String?
        get() = bitrateKbps?.takeIf { it > 0 }?.let { "${kotlin.math.round(it / 1000f).toInt()}Mbps" }

    fun durationDisplay(): String {
        val mins = durationMinutes ?: return "N/A"
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m}min"
    }
}
