package media.conduit.mobile

internal data class SubtitleCandidate<T>(
    val track: T,
    val language: String?,
    val title: String? = null,
    val embedded: Boolean,
)

internal sealed interface SubtitleDecision<out T> {
    data object Wait : SubtitleDecision<Nothing>
    data object Off : SubtitleDecision<Nothing>
    data class Select<T>(val track: T) : SubtitleDecision<T>
}

internal fun normalizeSubtitleLanguage(value: String?): String? {
    val base = value?.trim()?.lowercase()?.replace('_', '-')?.substringBefore('-')
        ?.takeIf(String::isNotBlank) ?: return null
    return when (base) {
        "english", "eng" -> "en"
        "spanish", "spa" -> "es"
        "french", "fra", "fre" -> "fr"
        "german", "deu", "ger" -> "de"
        "japanese", "jpn" -> "ja"
        "korean", "kor" -> "ko"
        else -> base
    }
}

internal fun matchesSubtitleLanguage(language: String, code: String?, title: String? = null): Boolean =
    normalizeSubtitleLanguage(language) == normalizeSubtitleLanguage(code) ||
        title.orEmpty().split(Regex("[^\\p{L}]+"))
            .any { normalizeSubtitleLanguage(it) == normalizeSubtitleLanguage(language) }

/** Language priority precedes embedded preference. An empty completed lookup permits fallback. */
internal fun <T> preferredSubtitle(
    candidates: List<SubtitleCandidate<T>>,
    primary: String,
    secondary: String?,
    systemLanguage: String,
    embeddedReady: Boolean,
    addonsReady: Boolean,
): SubtitleDecision<T> {
    if (!embeddedReady) return SubtitleDecision.Wait
    val languages = listOfNotNull(primary, secondary).mapNotNull {
        normalizeSubtitleLanguage(if (it == "System default") systemLanguage else it)
    }.distinct()
    fun matching(language: String) = candidates.filter { matchesSubtitleLanguage(language, it.language, it.title) }
    languages.firstOrNull()?.let { language ->
        matching(language).firstOrNull { it.embedded }?.let { return SubtitleDecision.Select(it.track) }
    }
    if (!addonsReady) return SubtitleDecision.Wait
    languages.forEach { language ->
        val matching = matching(language)
        (matching.firstOrNull { it.embedded } ?: matching.firstOrNull())?.let {
            return SubtitleDecision.Select(it.track)
        }
    }
    return SubtitleDecision.Off
}
