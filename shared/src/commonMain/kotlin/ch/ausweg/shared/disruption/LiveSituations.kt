package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.BusStatus
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.DisruptionText
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.ReplacementBus
import ch.ausweg.shared.model.Section
import ch.ausweg.shared.model.Situation
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.ValidityWindow
import kotlin.time.Instant

/** Prefix of [Disruption.id]s made from OJP situations, so they never clash with scenario ids. */
const val OJP_DISRUPTION_PREFIX = "ojp:"

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
 * This situation as a [Disruption] at [now], so live OJP data goes through the same `affects()` / `advise()` as
 * the replay. Pure.
 *
 * - **Sections** come from the text («zwischen Biel/Bienne und Grenchen Süd»), with both names found among
 *   [Situation.stops]. [Section.line] stays empty: OJP's stop list is only roughly in line order (Bern's S1 list
 *   ends with Bern after Thun), and a wrong order would make `affects()` hit trips outside the section. Without a
 *   section in the text, matching falls back to [Disruption.affectedStops] (a leg passing 2 of the stops).
 * - **Hints** from [SituationKeywords]. Most situations are restrictions with no hint: a warning only.
 *   [DisruptionHints.REPLACEMENT_BUS] also sets [Disruption.bus] (published, on the section).
 * - **Status:** ACTIVE inside a [Situation.validity] window (or with no windows), ENDED after the last one.
 *   [Disruption.expectedEnd] is the current window's end, unless the text says the end is unknown.
 * - [Disruption.texts]: the summary as title; description, consequence, recommendation and duration as body,
 *   untranslated, under [Situation.language] (`de` if unknown).
 *
 * Returns null when none of the stops has a DIDOK number (nothing to match trips against), or when [now] is
 * before or between windows (e.g. night works during the day): the disruption isn't on now.
 */
fun Situation.toDisruption(now: Instant): Disruption? {
    val stopNumbers = stops.mapNotNull { it.didok }.distinct()
    val phase = phaseAt(now)
    if (stopNumbers.isEmpty() || phase == null) return null

    val text = listOfNotNull(summary, description, consequence, recommendation, duration).joinToString(" ").folded()
    val hints = hintsIn(text)
    val sections = sectionsIn(text, stops)
    val openEnd = SituationKeywords.OPEN_END.any(text::contains)
    return Disruption(
        id = OJP_DISRUPTION_PREFIX + id,
        version = version ?: 1,
        publishedAt = recordedAt?.let { minOf(it, now) } ?: now,
        status = if (phase.ended) DisruptionStatus.ENDED else DisruptionStatus.ACTIVE,
        reasonCode = cause,
        affectedStops = stopNumbers,
        lines = lines,
        expectedEnd = phase.end.takeUnless { openEnd && !phase.ended },
        expectedEndType = if (openEnd) EndType.UNKNOWN else null,
        texts = mapOf((language ?: "de") to texts()),
        hints = hints,
        sections = sections,
        bus = busFor(hints, sections),
    )
}

/** Where a moment falls in [Situation.validity]: [end] of the window it is in (null: open), or after the last one. */
private class Phase(
    val ended: Boolean,
    val end: Instant?,
)

/** Null when [now] is before or between the windows. */
private fun Situation.phaseAt(now: Instant): Phase? {
    val window = validity.firstOrNull { it.contains(now) }
    return when {
        validity.isEmpty() -> Phase(ended = false, end = null)
        window != null -> Phase(ended = false, end = window.to)
        validity.all { it.to != null && it.to <= now } -> Phase(ended = true, end = validity.mapNotNull { it.to }.max())
        else -> null
    }
}

private fun hintsIn(text: String): List<String> =
    buildList {
        if (SituationKeywords.CLOSED.any(text::contains)) add(DisruptionHints.LINE_CLOSED)
        if (SituationKeywords.BUS.any(text::contains)) add(DisruptionHints.REPLACEMENT_BUS)
        if (SituationKeywords.NO_ALTERNATIVE.any(text::contains)) add(DisruptionHints.NO_ALTERNATIVE)
    }

/** Published replacement buses on the first section, when the text mentions them. */
private fun busFor(
    hints: List<String>,
    sections: List<Section>,
): ReplacementBus? =
    if (DisruptionHints.REPLACEMENT_BUS in hints) {
        ReplacementBus(BusStatus.PUBLISHED, sections.firstOrNull()?.from, sections.firstOrNull()?.to)
    } else {
        null
    }

private fun ValidityWindow.contains(now: Instant): Boolean = from <= now && (to == null || now < to)

private fun Situation.texts(): DisruptionText =
    DisruptionText(
        title = summary ?: description.orEmpty(),
        body =
            listOfNotNull(
                description.takeIf { summary != null },
                consequence,
                recommendation,
                duration,
            ).joinToString("\n"),
    )

/**
 * Every «between A and B» in [text] where A and B are names of [stops] (both [folded]). A name must be followed
 * by a non-name character, so «Genève» doesn't match «Genève-Aéroport».
 */
private fun sectionsIn(
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
