# Changelog

## Unreleased

- Add the MIT license and document the project's motivation, French language, and author attribution.
- Rename the app and project to **Plex Valet**, while retaining the installed app identity and local data.
- Soften the gold accent and launcher icon for use in a dark room.
- Add incremental refresh, live movie/stage progress, and a forced full refresh in Settings.
- Validate movie pagination, limit detail loading to four concurrent requests, and replace the cache in a single transaction.
- Preserve movies through database migrations 4→5→6, adding source-specific scores and sync fingerprints.
- Show verified Rotten Tomatoes critics and IMDb audience scores separately, including in filters and advisor metadata.
- Discover Plex players on demand, with separate loading, error, retry, and empty states; send a single controller identity header.
- React to saved connection settings and clear the refresh state even when the final catalog step fails.
- Document the app in English with three screenshots and separate development/privacy notes.
- Add root Git exclusions, a credential-free local configuration example, and the verified Gradle 8.6 wrapper with a pinned distribution checksum.
- Show the configured app version in Settings; disable HTTP logging in release builds and redact credential headers.
- Replace deprecated navigation/list icons with their mirrored variants.
- Remove the redundant movie-key screen parameter; the result ViewModel already receives the route argument through its saved state.

## v3.4.0

- NOUVEAUTÉ : Ajout du bouton "Caster sur TV" permettant d'envoyer la commande de lecture directement à une TV/Lecteur Plex via l'API locale Plex Companion (contourne les limitations de l'app Android officielle Plex).
- Ajout de la détection réseau des lecteurs Plex allumés.
- Implémentation du système de PlayQueue distant.

## v3.3.5

- ajout de la balise `<queries>` dans le Manifest pour Android 11+
- test de l'URI `plex://preplay/` pour tenter de restaurer l'ouverture de la fiche film dans l'app Plex
- copie du titre dans le presse-papier en fallback si Plex bloque le deep link et ouvre le menu principal

## v3.3.4

- remplacement du lien `plex://play` par une URL de fiche Plex Web ciblee vers l'app Android officielle
- fallback vers la fiche Plex Web dans le navigateur si l'app Plex ne prend pas le lien
- correction du package Play Store Plex vers `com.plexapp.android`

## v3.3.3

- ajustement du deep link `Lancer Plex` vers `plex://play/`
- recuperation automatique du `machineId` Plex si absent dans les preferences locales

## v3.3.2

- correction du deep link `Lancer Plex` pour rouvrir la fiche du film dans l'app Plex
- conservation de la copie du titre du film dans le clipboard

## v3.3.1

- suppression du sous-titre gris dans le header principal
- titre principal agrandi dans le top bar
- renommage du chat AI
- suppression de la limite de taille de preselction pour l'agent AI
- harmonisation des couleurs des boutons `random` et `playlist`

## v3.3.0

- ajout du bouton `AI` dans le top bar, a droite des parametres
- activation de l'AI seulement lorsqu'au moins un vrai filtre differe des valeurs par defaut
- ajout d'une fenetre de chat AI dans l'ecran principal
- integration de Claude via `claude-haiku-4-5`
- limitation de la preselction AI pour eviter les prompts trop couteux
- lecture de `ANTHROPIC_API_KEY` depuis `local.properties`
- ajout de `README.md` et `CHANGELOG.md`

## v3.2.0

- ajout de la recherche manuelle par titre sur l'ecran principal
- ouverture directe de la fiche film depuis les resultats de recherche

## v3.1.6

- indexation et affichage du HDR dans la fiche film et la playlist
