package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.Section
import ch.ausweg.shared.model.StopRef

/**
 * Keyword rules that read Swiss situation texts (fixed SBB templates, DE/FR/IT/EN). Matched on lowercase text
 * without accents. No AI model: only these lists.
 */
object SituationKeywords {
    /** «Der Bahnverkehr zwischen X und Y ist unterbrochen.» → [DisruptionHints.LINE_CLOSED]. */
    val CLOSED =
        listOf(
            "unterbrochen",
            "keine zuge",
            "interrompu",
            "aucun train",
            "interrott",
            "nessun treno",
            "interrupted",
            "no trains",
        )

    /** «… benützen die Ersatzbusse (EV).» → [DisruptionHints.REPLACEMENT_BUS]. */
    val BUS = listOf("ersatzbus", "bahnersatz", "bus de remplacement", "bus sostitutiv", "replacement bus")

    /** → [DisruptionHints.NO_ALTERNATIVE]. */
    val NO_ALTERNATIVE = listOf("keine alternative", "aucune alternative", "nessuna alternativa", "no alternative")

    /** «Die Dauer der Einschränkung ist unbestimmt.» → open end, whatever the validity says. */
    val OPEN_END = listOf("unbestimmt", "indeterminee", "indeterminata", "duration is unknown", "until further notice")

    /** «zwischen X und Y» in each language: the section the situation is about. */
    val BETWEEN = listOf("zwischen" to "und", "entre" to "et", "tra" to "e", "between" to "and")
}

/**
 * Every «between A and B» in [text] where A and B are names of [stops] (both [folded]). A name must be followed
 * by a non-name character, so «Genève» doesn't match «Genève-Aéroport».
 */
internal fun sectionsIn(
    text: String,
    stops: List<StopRef>,
): List<Section> {
    val named =
        stops.mapNotNull { stop ->
            stop.didok?.let {
                stop.name
                    .folded()
                    .removeSuffix(" (i)")
                    .trim() to it
            }
        }
    return SituationKeywords.BETWEEN
        .flatMap { (between, and) ->
            named.flatMap { (a, from) ->
                named.mapNotNull { (b, to) ->
                    if (from != to && text.containsPhrase("$between $a $and $b")) Section(from, to) else null
                }
            }
        }.distinctBy { setOf(it.from, it.to) }
}

/** True if [phrase] is in this text as whole words: not preceded or followed by a letter, digit or hyphen. */
private fun String.containsPhrase(phrase: String): Boolean {
    var i = indexOf(phrase)
    while (i >= 0) {
        val before = getOrNull(i - 1)
        val after = getOrNull(i + phrase.length)
        if (!before.isNameChar() && !after.isNameChar()) return true
        i = indexOf(phrase, i + 1)
    }
    return false
}

private fun Char?.isNameChar(): Boolean = this != null && (isLetterOrDigit() || this == '-')

private val ACCENTS =
    mapOf(
        'à' to 'a',
        'â' to 'a',
        'ä' to 'a',
        'á' to 'a',
        'ç' to 'c',
        'é' to 'e',
        'è' to 'e',
        'ê' to 'e',
        'ë' to 'e',
        'î' to 'i',
        'ï' to 'i',
        'í' to 'i',
        'ì' to 'i',
        'ô' to 'o',
        'ö' to 'o',
        'ó' to 'o',
        'ò' to 'o',
        'ù' to 'u',
        'û' to 'u',
        'ü' to 'u',
        'ú' to 'u',
    )

/** Lowercase, without accents and with single spaces, so «Gümligen» and «gumligen» compare equal. */
internal fun String.folded(): String =
    lowercase()
        .map { ACCENTS[it] ?: it }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
