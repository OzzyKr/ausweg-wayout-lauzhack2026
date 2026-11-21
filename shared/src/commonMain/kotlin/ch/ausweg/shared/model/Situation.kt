package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** One period a [Situation] applies to. [to] is null when the end is open. */
@Serializable
data class ValidityWindow(
    val from: Instant,
    val to: Instant? = null,
)

/**
 * A disruption notice OJP attaches to trip results and departure boards (SIRI `PtSituation`), as published,
 * untranslated. OJP answers in the one language the request asked for.
 *
 * Swiss situations carry no structured SIRI consequence: what happens (restricted, interrupted, replacement
 * buses) is only in the texts, which follow fixed SBB templates («Der Bahnverkehr zwischen X und Y ist
 * unterbrochen.»).
 *
 * @property validFrom start of the first [validity] window.
 * @property validTo end of the last [validity] window; null if it is open.
 * @property validity every period the situation applies to. Construction work often repeats (every night
 *   22:30–05:55), so there can be many.
 * @property language lowercase code (`de`, `fr`, `it`, `en`) of the texts.
 * @property version increases with every update of the same [id].
 * @property recordedAt when this version's text was last updated (latest `RecordedAtTime`), else `CreationTime`.
 * @property cause SIRI `AlertCause`, e.g. `constructionWork`.
 * @property planned true for announced work, false for incidents.
 * @property stops affected stations, in the order OJP lists them (roughly along the line), without duplicates.
 * @property lines published line names (`RE1`, `S3`) when OJP names them; often empty.
 * @property consequence what happens, e.g. «Die Linie S20 fällt zwischen Biel/Bienne und Grenchen Süd aus.»
 * @property recommendation what travellers should do, e.g. «… benützen die Ersatzbusse (EV).»
 * @property duration the published period as text, e.g. «Die Einschränkung dauert von … bis …».
 */
@Serializable
data class Situation(
    val id: String,
    val summary: String? = null,
    val description: String? = null,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val language: String? = null,
    val version: Int? = null,
    val recordedAt: Instant? = null,
    val cause: String? = null,
    val planned: Boolean = false,
    val validity: List<ValidityWindow> = emptyList(),
    val stops: List<StopRef> = emptyList(),
    val lines: List<String> = emptyList(),
    val consequence: String? = null,
    val recommendation: String? = null,
    val duration: String? = null,
)
