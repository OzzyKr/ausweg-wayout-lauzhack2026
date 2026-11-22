package ch.ausweg.server.ingest

import ch.ausweg.shared.model.BusStatus
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.InfoSource
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/*
 * Scenario files (`data/scenarios`, one `.events.json` each), as documented in `data/README.md`
 * («Format d'un scénario») and checked by `data/scripts/validate.py`. Only the fields the server uses are declared.
 *
 * There is deliberately no `raw` field: the original (internal) text is never even read into memory here, so it
 * can't reach the app or the logs. Unknown keys are ignored by `AuswegJson`.
 */

@Serializable
internal data class ScenarioFile(
    val incident: Incident,
    val events: List<ScenarioEvent>,
)

/**
 * @property stationIds official station name to DIDOK number, for every station named in the events.
 * @property titles [title] in other languages than [SCENARIO_LANGUAGE] (`en`, `de`, `it`), shown in the UI language.
 * @property suggestedFrom optional station name to prefill the plan screen with; defaults to the first of
 *   [lineStations] (and [suggestedTo] to the last).
 */
@Serializable
internal data class Incident(
    val id: String,
    val title: String,
    val titles: Map<String, String> = emptyMap(),
    val lines: List<String> = emptyList(),
    val lineStations: List<String> = emptyList(),
    val stationIds: Map<String, Int> = emptyMap(),
    val replayStart: Instant,
    val replayEnd: Instant,
    val suggestedFrom: String? = null,
    val suggestedTo: String? = null,
)

@Serializable
internal data class NamedSection(
    val from: String,
    val to: String,
)

@Serializable
internal data class EventEnd(
    val time: Instant? = null,
    val type: EndType,
)

@Serializable
internal data class EventBus(
    val status: BusStatus,
    val from: String? = null,
    val to: String? = null,
    val eta: Instant? = null,
    val count: Int? = null,
    val routes: List<NamedSection> = emptyList(),
)

/**
 * One event. [kind] stays a string so a new kind in the data doesn't break loading (it's then text only).
 *
 * @property summaries [summary] in other languages than [SCENARIO_LANGUAGE] (`en`, `de`, `it`).
 */
@Serializable
internal data class ScenarioEvent(
    val t: Instant,
    val source: InfoSource,
    val kind: String,
    val summary: String,
    val summaries: Map<String, String> = emptyMap(),
    val sections: List<NamedSection>? = null,
    val expectedEnd: EventEnd? = null,
    val bus: EventBus? = null,
    val noAlternative: Boolean = false,
)
