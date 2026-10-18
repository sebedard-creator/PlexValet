# Development notes

## Project layout

Kotlin and Jetpack Compose provide the Android UI. Hilt supplies dependencies, Retrofit/OkHttp talk to Plex, Room stores the movie cache, and DataStore stores connection preferences and the collection catalog.

- `ui/`: filter, advisor, result, playlist, and settings screens with their ViewModels.
- `domain/`: movie models and filtering/selection use cases.
- `data/remote/`: Plex models, API declarations, and request headers.
- `data/repository/`: sync, metadata enrichment, casting, and Claude requests.
- `data/local/`: Room entities, DAO, and database migrations.
- `app/src/test/`: pagination, concurrent loading, incremental sync, ratings, filtering, headers, and player-discovery error tests.

Sources remain under `app/src/main/java/com/seb/sdpr`. The application ID (`com.seb.sdpr`), database name (`sdpr_db`), preferences name (`sdpr_prefs`), and Plex controller ID (`sdpr-android-v1`) are intentionally retained so existing installations keep their data and identity.

## Building and checking changes

The wrapper selects Gradle 8.6 with a pinned distribution checksum; its JAR has been checked against Gradle's published checksum. The project uses Android Gradle Plugin 8.3.2, Kotlin 1.9.22, compile/target SDK 34, and minimum SDK 26. Use JDK 17 or 21.

1. Open the project in Android Studio and install SDK 34 when prompted.
2. Let Android Studio create `local.properties` with your SDK location. `local.properties.example` documents the optional Anthropic key; it contains no credentials.
3. Run `sh ./gradlew assembleDebug testDebugUnitTest lintDebug` (`.\gradlew.bat` on Windows).
4. Install the generated debug APK locally if a device check is needed. Rebuild after changing the Anthropic key.

Keep the README concise; record implementation details here and user-visible changes under **Unreleased** in the changelog. The version displayed in Settings comes from `BuildConfig.VERSION_NAME`, configured in `app/build.gradle`.

## Library refresh

The toolbar refresh performs an incremental sync. It still enumerates every movie so additions and deletions are detected. Movie details are reused only when a stored fingerprint matches the server identity, library section, Plex `updatedAt` marker, and the relevant list metadata/media identifiers. Watch counts are updated separately. Without a usable modification marker or server identity, details are fetched again. Failed detail requests leave no reusable fingerprint and are retried on the next refresh.

The first scan builds the reference. **Paramètres → Bibliothèque locale → Forcer un refresh complet** bypasses it, fetches every movie's details, and rebuilds the reference. A change Plex does not reflect in the fingerprint inputs can require a full refresh.

Movie pages are validated before use. A shared semaphore limits detail requests to four concurrent calls (`PlexMovieDetailsLoader.MAX_CONCURRENT_DETAILS`). Background processing and per-movie progress keep the screen responsive. Counts and bars refer to the current stage and library section; collections, database saving, the final catalog, and preference saving have separate status labels.

The repository downloads the movie data before replacing it in one Room transaction. Invalid movie pagination or cancellation before that write preserves the previous library. Collections are checked again even when movie details are reused; incomplete collection reads preserve prior memberships rather than removing them. The collection catalog and last-sync preference are saved after the movie transaction, so a failure at that final stage can occur after the movie cache has already been refreshed.

The database is version 6. Migrations 4→5 add the two source-specific rating columns; 5→6 adds the nullable sync fingerprint. Both preserve existing movies. Older database versions without a migration use the configured destructive fallback to rebuild the cache.

The Android `PlexSync` log records movie totals, reuse counts, the library phase duration, and the complete refresh duration including the catalog. It does not log credentials or movie titles.

## Ratings, languages, and selection

- Rotten Tomatoes critics ratings and IMDb audience ratings are resolved by their Plex source identifiers. A missing source stays unknown and displays `N/A`; generic Plex ratings are not relabeled as either source.
- An active Rotten Tomatoes score range excludes movies without a verified score. The default full range includes them.
- **Intentional behavior:** movies without audio-language metadata are treated as French. Do not remove this fallback as a cleanup.
- Genre, collection, content rating, decade, language, runtime, resolution, and watched filters use the cached metadata. Plex collections, including smart collections, can be queried live when selected.
- The playlist screen is a browsable list of matches, not a saved playlist created on the Plex server.

## Playback and the optional advisor

Opening **Caster** discovers Plex players on demand; loading, errors, retry, and an empty list have separate UI states. `PlexHeadersInterceptor` adds the app's controller identity once. The selected player's identifier uses a separate `X-Plex-Target-Client-Identifier` header. Playback uses a Plex PlayQueue and a Plex Companion command; support depends on the player.

The Plex button copies the movie title and tries a Plex deep link, with app-launch/Play Store fallbacks. It cannot determine whether another app ultimately opened the intended movie page. Trailer lookup opens a YouTube search.

The AI button becomes available when at least one actual filter differs from its default. Title search alone does not enable it. Claude receives the full filtered shortlist and the chat history; there is no candidate-count cap. The requested model is configured as `ANTHROPIC_MODEL` in `app/build.gradle`. Sending chat requests can incur Anthropic usage charges.

## Private data and publishing

Keep real credentials in ignored local files. Enter the Plex server address and token in the app; they are stored on the device in DataStore. Optional `ANTHROPIC_API_KEY` comes from `local.properties` and is compiled into `BuildConfig`, which means it can be extracted from a personal APK. Do not publish a keyed APK; a distributable app needs a different credential-management approach.

The advisor sends chat messages and selected movie metadata (including titles, short summaries, collections, and watched status) to Anthropic over HTTPS. Plex requests use HTTP on the configured local network and send the Plex token to that server. Settings and the movie cache are private device data; Android backup is currently enabled in the manifest. None of these device files belong in Git.

The root `.gitignore` excludes local properties, environment files, signing material, IDE state, build output, APKs, databases, preferences, logs, and backup archives. Build output is especially important because generated source and packages may contain the Anthropic key. Only the three reviewed screenshots in `docs/screenshots/` are intended for publication.

Before committing, inspect `git status --short` and `git diff --cached`. A `.gitignore` applies to untracked files; it does not remove files already tracked or secrets from Git history. If an actual credential has previously been published, revoke it and remove it from the repository history separately.
