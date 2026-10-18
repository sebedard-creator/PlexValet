package com.seb.sdpr.data.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.seb.sdpr.BuildConfig
import com.seb.sdpr.domain.model.Movie
import com.seb.sdpr.ui.filter.AiChatMessage
import com.seb.sdpr.ui.filter.AiChatRole
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class ClaudeMovieAdvisorRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()

    fun hasApiKey(): Boolean = BuildConfig.ANTHROPIC_API_KEY.trim().isNotEmpty()

    suspend fun chat(
        candidates: List<Movie>,
        conversation: List<AiChatMessage>
    ): Result<String> = runCatching {
        val apiKey = BuildConfig.ANTHROPIC_API_KEY.trim()
        require(apiKey.isNotEmpty()) {
            "Cle Anthropic absente. Ajoute `ANTHROPIC_API_KEY=` dans `local.properties`, puis rebuild l'app."
        }

        val requestBody = ClaudeMessagesRequest(
            model = BuildConfig.ANTHROPIC_MODEL.ifBlank { "claude-haiku-4-5" },
            maxTokens = 500,
            system = buildSystemPrompt(candidates),
            temperature = 0.6,
            messages = conversation.mapNotNull { message ->
                when (message.role) {
                    AiChatRole.USER -> ClaudeInputMessage(role = "user", content = message.text)
                    AiChatRole.ASSISTANT -> ClaudeInputMessage(role = "assistant", content = message.text)
                    AiChatRole.NOTICE -> null
                }
            }
        )
        require(requestBody.messages.isNotEmpty()) { "Aucun message a envoyer." }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val rawBody = withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMessage = runCatching {
                        gson.fromJson(responseBody, ClaudeErrorEnvelope::class.java)?.error?.message
                    }.getOrNull()
                    error(errorMessage ?: "Claude a retourne HTTP ${response.code}")
                }
                responseBody
            }
        }

        val parsed = gson.fromJson(rawBody, ClaudeMessageResponse::class.java)
        val text = parsed.content.orEmpty()
            .filter { it.type == "text" }
            .joinToString("\n") { it.text.orEmpty().trim() }
            .trim()

        if (text.isBlank()) error("Claude n'a pas retourne de texte exploitable.")
        text
    }

    private fun buildSystemPrompt(candidates: List<Movie>): String {
        return buildString {
            appendLine("Tu es le sommelier cinema de l'application Plex Valet.")
            appendLine("Tu ne peux recommander que des films presents dans le catalogue fourni.")
            appendLine("N'invente jamais un film et ne sors jamais de ce catalogue.")
            appendLine("Quand l'utilisateur demande des suggestions, propose entre 3 et 5 films maximum.")
            appendLine("Pour chaque recommandation, donne le titre exact puis une justification courte et concrete.")
            appendLine("Si aucun film n'est un bon match, dis-le franchement et suggere quels filtres resserrer ou elargir.")
            appendLine("Reponds dans la langue de l'utilisateur. Par defaut, reponds en francais.")
            appendLine()
            appendLine("Catalogue filtre (${candidates.size} films) :")
            append(buildCandidateCatalog(candidates))
        }
    }

    private fun buildCandidateCatalog(candidates: List<Movie>): String =
        candidates.joinToString("\n") { movie ->
            val parts = mutableListOf<String>()
            movie.year?.let { parts += "annee=$it" }
            if (movie.genres.isNotEmpty()) parts += "genres=${movie.genres.take(4).joinToString("/")}"
            if (movie.collections.isNotEmpty()) parts += "collections=${movie.collections.take(3).joinToString("/")}"
            movie.contentRating?.takeIf { it.isNotBlank() }?.let { parts += "classification=$it" }
            movie.rottenTomatoesPercent?.let { parts += "rt_critics=${it}%" }
            movie.imdbRating?.let { parts += "imdb=${String.format(Locale.US, "%.1f", it)}/10" }
            if (movie.audioLanguages.isNotEmpty()) parts += "audio=${movie.audioLanguages.take(3).joinToString("/")}"
            parts += if (movie.viewCount > 0) "vu=oui" else "vu=non"

            val summary = movie.summary
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(180)

            buildString {
                append("- ${movie.title}")
                if (parts.isNotEmpty()) append(" | ${parts.joinToString(" | ")}")
                if (!summary.isNullOrBlank()) append(" | synopsis=$summary")
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

private data class ClaudeMessagesRequest(
    val model: String,
    @SerializedName("max_tokens") val maxTokens: Int,
    val system: String,
    val temperature: Double,
    val messages: List<ClaudeInputMessage>
)

private data class ClaudeInputMessage(
    val role: String,
    val content: String
)

private data class ClaudeMessageResponse(
    val content: List<ClaudeContentBlock>?
)

private data class ClaudeContentBlock(
    val type: String?,
    val text: String?
)

private data class ClaudeErrorEnvelope(
    val error: ClaudeErrorBody?
)

private data class ClaudeErrorBody(
    val message: String?
)
