package ch.ausweg.server.ingest

import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.Scenario
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.TimelineEntry

/** One published version of a disruption. [deleted] versions withdraw the disruption (entered by mistake). */
data class DisruptionUpdate(
    val disruption: Disruption,
    val deleted: Boolean = false,
)

/**
 * Every disruption version we know about, past and future relative to the clock, plus replayable scenarios.
 *
 * @property timeline the history behind the versions (one entry per data event), filtered by time like [updates].
 * @property stations every station the data names, with its DIDOK number (offline stop search).
 */
data class DisruptionData(
    val updates: List<DisruptionUpdate>,
    val scenarios: List<Scenario>,
    val timeline: List<TimelineEntry> = emptyList(),
    val stations: List<StopRef> = emptyList(),
) {
    operator fun plus(other: DisruptionData) =
        DisruptionData(
            updates + other.updates,
            scenarios + other.scenarios,
            timeline + other.timeline,
            (stations + other.stations).distinctBy { it.didok },
        )

    companion object {
        val EMPTY = DisruptionData(emptyList(), emptyList())
    }
}

/**
 * Where disruption data comes from. Callers filter by publication time themselves, so the same source
 * serves historical replay and live mode. Implementations must be cheap to call repeatedly.
 */
fun interface DisruptionSource {
    fun load(): DisruptionData
}

/** [primary]'s data, or [fallback]'s while [primary] has none (e.g. no scenario files yet). */
class FallbackSource(
    private val primary: DisruptionSource,
    private val fallback: DisruptionSource,
) : DisruptionSource {
    override fun load(): DisruptionData = primary.load().takeIf { it.updates.isNotEmpty() } ?: fallback.load()
}
