package com.seb.sdpr.util

private val LANGUAGE_NAMES = mapOf(
    "fr" to "Francais",
    "en" to "English",
    "es" to "Espanol",
    "de" to "Deutsch",
    "it" to "Italiano",
    "pt" to "Portugues",
    "ja" to "Japanese",
    "zh" to "Chinese",
    "ko" to "Korean",
    "ru" to "Russian",
    "ar" to "Arabic",
    "nl" to "Nederlands",
    "sv" to "Svenska",
    "no" to "Norsk",
    "da" to "Dansk",
    "fi" to "Suomi",
    "pl" to "Polski",
    "tr" to "Turkce",
    "he" to "Hebrew",
    "hu" to "Magyar",
    "cs" to "Cestina",
    "ca" to "Catala"
)

private val LANGUAGE_ALIASES = mapOf(
    "fre" to "fr",
    "fra" to "fr",
    "french" to "fr",
    "francais" to "fr",
    "français" to "fr",
    "francese" to "fr",
    "unknown" to "fr",
    "inconnu" to "fr",
    "und" to "fr",
    "eng" to "en",
    "english" to "en",
    "spa" to "es",
    "espanol" to "es",
    "español" to "es",
    "spanish" to "es",
    "ger" to "de",
    "deu" to "de",
    "german" to "de",
    "ita" to "it",
    "italian" to "it",
    "por" to "pt",
    "portuguese" to "pt",
    "jpn" to "ja",
    "japanese" to "ja",
    "chi" to "zh",
    "zho" to "zh",
    "chinese" to "zh",
    "kor" to "ko",
    "korean" to "ko",
    "rus" to "ru",
    "russian" to "ru",
    "ara" to "ar",
    "arabic" to "ar",
    "dut" to "nl",
    "nld" to "nl",
    "dutch" to "nl",
    "swe" to "sv",
    "swedish" to "sv",
    "nor" to "no",
    "norwegian" to "no",
    "dan" to "da",
    "danish" to "da",
    "fin" to "fi",
    "finnish" to "fi",
    "pol" to "pl",
    "polish" to "pl",
    "tur" to "tr",
    "turkish" to "tr",
    "heb" to "he",
    "hebrew" to "he",
    "hun" to "hu",
    "hungarian" to "hu",
    "ces" to "cs",
    "cze" to "cs",
    "czech" to "cs",
    "cat" to "ca",
    "catalan" to "ca"
)

fun String.toDisplayLanguage(): String {
    val canonical = normalizeLanguageCode(this)
    return LANGUAGE_NAMES[canonical] ?: canonical.uppercase()
}

fun normalizeLanguageCode(code: String): String {
    val cleaned = code.trim().lowercase()
    if (cleaned.isBlank()) return "fr"

    val primary = cleaned
        .substringBefore('-')
        .substringBefore('_')
        .substringBefore(' ')

    return when {
        LANGUAGE_NAMES.containsKey(primary) -> primary
        LANGUAGE_ALIASES.containsKey(primary) -> checkNotNull(LANGUAGE_ALIASES[primary])
        LANGUAGE_ALIASES.containsKey(cleaned) -> checkNotNull(LANGUAGE_ALIASES[cleaned])
        else -> primary
    }
}
