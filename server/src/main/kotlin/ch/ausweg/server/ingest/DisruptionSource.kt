package ch.ausweg.server.ingest

import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.Scenario

/** One published version of a disruption. [deleted] versions withdraw the disruption (entered by mistake). */
data class DisruptionUpdate(
    val disruption: Disruption,
    val deleted: Boolean = false,
)

/** Every disruption version we know about, past and future relative to the clock, plus replayable scenarios. */
data class DisruptionData(
    val updates: List<DisruptionUpdate>,
    val scenarios: List<Scenario>,
) {
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
