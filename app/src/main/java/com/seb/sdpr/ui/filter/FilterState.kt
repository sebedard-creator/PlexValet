package com.seb.sdpr.ui.filter

import java.io.Serializable

enum class WatchedFilter { ALL, UNWATCHED, WATCHED }

data class FilterState(
    val selectedGenre:         String?       = null,
    val selectedCollection:    String?       = null,
    val selectedContentRatings: List<String> = emptyList(),
    val selectedDecade:        String?       = null,
    val rtScoreMin:            Int           = 0,
    val rtScoreMax:            Int           = 100,
    val selectedLanguage:      String?       = null,
    val maxDurationMinutes:    Int           = 240,
    val selectedResolution:    String?       = null,
    val watchedFilter:         WatchedFilter = WatchedFilter.ALL
) : Serializable
