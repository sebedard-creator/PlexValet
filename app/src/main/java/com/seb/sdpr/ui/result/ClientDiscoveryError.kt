package com.seb.sdpr.ui.result

import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.HttpException

internal fun clientDiscoveryErrorMessage(error: Exception): String = when (error) {
    is HttpException -> when (error.code()) {
        401, 403 -> "Accès au serveur Plex refusé. Vérifie le token dans les paramètres."
        else -> "Le serveur Plex a retourné une erreur (HTTP ${error.code()}). Réessaie."
    }
    is SocketTimeoutException -> "Le serveur Plex ne répond pas à temps. Vérifie qu'il est allumé, puis réessaie."
    is IOException -> "Impossible de joindre le serveur Plex. Vérifie son adresse et ta connexion réseau."
    is IllegalArgumentException -> "Configuration Plex invalide. Vérifie l'adresse du serveur et le token dans les paramètres."
    else -> "Impossible de récupérer la liste des lecteurs Plex. Réessaie."
}
