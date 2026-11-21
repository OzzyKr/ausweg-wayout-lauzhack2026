package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class DisruptionStatus { ACTIVE, ENDED }

/** Title and body of a disruption in one language, as SBB published it (not translated by us). */
@Serializable
data class DisruptionText(
    val title: String,
    val body: String = "",
)

/**
 * A stretch of line between two stations, as DIDOK numbers (e.g. Puidoux 8504012 – Vauderens 8504021).
 * Direction doesn't matter: a train from [to] to [from] is in the section too.
 *
 * @property line every station of the line the section is on, in order (DIDOK), when the data knows it. OJP only
 *   lists the stops a train serves, so an IC running Lausanne → Fribourg non-stop never shows Puidoux or Vauderens;
 *   with [line], matching sees that it runs through the section anyway. Empty for sections on lines we don't know.
 */
@Serializable
data class Section(
    val from: Int,
    val to: Int,
    val line: List<Int> = emptyList(),
)

/** How firm [Disruption.expectedEnd] is, as the published text says it («environ», «au minimum»…). */
@Serializable
enum class EndType { APPROX, AT_LEAST, UNKNOWN }

/** Where the replacement bus stands in the data: ordered, scheduled with a time, or published to travellers. */
@Serializable
enum class BusStatus { ORDERED, SCHEDULED, PUBLISHED }

/**
 * Replacement buses for a disruption. Everything but [status] is often unknown in the data.
 *
 * @property from DIDOK number of the station the buses leave from.
 * @property to DIDOK number of the station they go to, if there is a single one; see [routes] otherwise.
 * @property eta when the buses are at [from], ready to leave.
 * @property routes the bus routes when buses go to several stations.
 */
@Serializable
data class ReplacementBus(
    val status: BusStatus,
    val from: Int? = null,
    val to: Int? = null,
    val eta: Instant? = null,
    val count: Int? = null,
    val routes: List<Section> = emptyList(),
)

/** Values of [Disruption.hints]. The server maps the data's event `kind` and flags onto these; nothing else. */
object DisruptionHints {
    /** No trains on the [Disruption.sections] (data kind `INTERRUPTION`). */
    const val LINE_CLOSED = "LINE_CLOSED"

    /** Trains replaced by buses on the sections (data kind `REPLACEMENT_BUS`). */
    const val REPLACEMENT_BUS = "REPLACEMENT_BUS"

    /** The published text says there is no alternative (data flag `noAlternative`). */
    const val NO_ALTERNATIVE = "NO_ALTERNATIVE"

    /** Hints that stop a trip through the sections and so trigger Wait / Detour / TakeBus advice. */
    val BLOCKING: Set<String> = setOf(LINE_CLOSED, REPLACEMENT_BUS)
}

/**
 * One disruption as it was known at [publishedAt]: the latest published version.
 *
 * @property sections affected stretches of line, matched against trip legs by DIDOK number. One disruption can
 *   close several (Puidoux – Vauderens **and** Puidoux – Lucens).
 * @property affectedStops DIDOK numbers of affected stations. Older, flat form of [sections], used for matching
 *   only when [sections] is empty.
 * @property expectedEndType how firm [expectedEnd] is; null when the data doesn't say.
 * @property bus replacement buses, if any are known.
 * @property texts language code (`de`, `fr`, `it`, `en`) to text.
 * @property hints [DisruptionHints] values.
 */
@Serializable
data class Disruption(
    val id: String,
    val version: Int,
    val publishedAt: Instant,
    val status: DisruptionStatus,
    val reasonCode: String? = null,
    val affectedStops: List<Int> = emptyList(),
    val lines: List<String> = emptyList(),
    val expectedEnd: Instant? = null,
    val texts: Map<String, DisruptionText> = emptyMap(),
    val hints: List<String> = emptyList(),
    val sections: List<Section> = emptyList(),
    val expectedEndType: EndType? = null,
    val bus: ReplacementBus? = null,
)
