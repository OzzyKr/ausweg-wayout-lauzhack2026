package ch.ausweg.server.ingest

import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.DisruptionText
import ch.ausweg.shared.model.Scenario
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * PROVISIONAL `events.json` layout until the data owner fixes the real one.
 * When it changes, only this file (and the fixtures) should need to change.
 */
@Serializable
internal data class EventsFile(
    val updates: List<UpdateEntry> = emptyList(),
    val scenarios: List<Scenario>? = null,
)

@Serializable
internal data class UpdateEntry(
    val disruptionId: String,
    val version: Int = 0,
    val publishedAt: Instant,
    /** VIA lifecycle: `INFO` (ongoing), `ENDED` or `DELETED`. */
    val lifecycle: String = "INFO",
    val reasonCode: String? = null,
    val affectedStops: List<Int> = emptyList(),
    val lines: List<String> = emptyList(),
    val expectedEnd: Instant? = null,
    val texts: Map<String, DisruptionText> = emptyMap(),
    val hints: List<String> = emptyList(),
)

internal fun EventsFile.toData(): DisruptionData {
    val updates = updates.map { it.toUpdate() }
    return DisruptionData(updates, scenarios ?: deriveScenarios(updates))
}

private fun UpdateEntry.toUpdate(): DisruptionUpdate {
    val lifecycle = lifecycle.uppercase()
    return DisruptionUpdate(
        disruption =
            Disruption(
                id = disruptionId,
                version = version,
                publishedAt = publishedAt,
                status = if (lifecycle == "INFO") DisruptionStatus.ACTIVE else DisruptionStatus.ENDED,
                reasonCode = reasonCode,
                affectedStops = affectedStops,
                lines = lines,
                expectedEnd = expectedEnd,
                texts = texts,
                hints = hints,
            ),
        deleted = lifecycle == "DELETED",
    )
}
