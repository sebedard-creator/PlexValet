package com.seb.sdpr.data.repository

import com.seb.sdpr.data.remote.MoviesContainer
import com.seb.sdpr.data.remote.PlexMovie

internal class PlexMoviePageValidator(private val sectionLabel: String) {
    var nextStart: Int = 0
        private set
    var isComplete: Boolean = false
        private set

    private var expectedTotal: Int? = null
    val total: Int? get() = expectedTotal
    private val seenKeys = mutableSetOf<String>()

    fun validate(container: MoviesContainer?): List<PlexMovie> {
        if (container == null) incomplete("réponse Plex sans conteneur")
        val movies = container.metadata.orEmpty()
        // A declared size of zero is also valid for an empty library without Metadata.
        val total = container.totalSize ?: expectedTotal
            ?: if (container.size == 0 && movies.isEmpty()) 0
            else incomplete("nombre total de films absent")

        if (total < 0) incomplete("nombre total de films invalide")
        if (expectedTotal != null && total != expectedTotal) {
            incomplete("le nombre de films a changé pendant la récupération")
        }
        if (container.size != null && container.size != movies.size) {
            incomplete("la taille annoncée de la page ne correspond pas aux films reçus")
        }
        if (movies.isEmpty() && nextStart < total) {
            incomplete("page vide après $nextStart films reçus sur $total attendus")
        }
        if (nextStart.toLong() + movies.size > total) {
            incomplete("davantage de films reçus que le total annoncé")
        }

        val pageKeys = mutableSetOf<String>()
        for (movie in movies) {
            val key = movie.ratingKey
            if (key.isNullOrBlank()) incomplete("un film ne possède pas d'identifiant")
            if (movie.title.isNullOrBlank()) incomplete("un film ne possède pas de titre")
            if (key in seenKeys || !pageKeys.add(key)) {
                incomplete("film reçu plusieurs fois, pagination incohérente")
            }
        }

        seenKeys += pageKeys
        expectedTotal = total
        nextStart += movies.size
        isComplete = nextStart == total
        return movies
    }

    private fun incomplete(reason: String): Nothing =
        error("Synchronisation interrompue ($sectionLabel) : $reason. Bibliothèque locale conservée.")
}
