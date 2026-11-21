package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Where a piece of disruption information came from. */
@Serializable
enum class InfoSource {
    /** SBB's upstream disruption system (incident created, first publication…). */
    VIA,

    /** SBB's internal ticker (control centre, stations). Always paraphrased, never shown raw. */
    INTERNAL,

    /** What travellers could read in the public live ticker. */
    PUBLIC,
}

/**
 * One step in a disruption's history, for the timeline on the live trip screen.
 *
 * @property kind the data's event kind (`INTERRUPTION`, `PROGNOSIS`, `REPLACEMENT_BUS`, `ENDED`…).
 * @property summary a short paraphrase in the data's language (French for the demo scenarios), never raw internal text.
 */
@Serializable
data class TimelineEntry(
    val disruptionId: String,
    val t: Instant,
    val source: InfoSource,
    val kind: String,
    val summary: String,
)
