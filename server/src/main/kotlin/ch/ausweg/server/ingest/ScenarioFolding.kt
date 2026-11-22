package ch.ausweg.server.ingest

import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.DisruptionText
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.ReplacementBus
import ch.ausweg.shared.model.Scenario
import ch.ausweg.shared.model.Section
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.TimelineEntry
import kotlin.time.Instant

/** Language of the scenario `title` and `summary` texts; `titles` and `summaries` hold the others. */
internal const val SCENARIO_LANGUAGE = "fr"

/**
 * Turns one scenario file into disruption versions: every event publishes a new version of the incident, carrying
 * forward what earlier events said (sections, expected end, bus) and changing only what this event says.
 *
 * Section rules (the data's events don't always repeat the full scope):
 * - Only closing kinds (`INTERRUPTION`, `DETECTED`, `SEVERITY_UP`) naming sections close the line. While it is
 *   closed, they add their sections; after a restriction or restoration, they **replace** them and close it again.
 * - Other kinds (`PROGNOSIS`, `INFO`, `SCOPE_CHANGE`…) naming sections **add** them while the line is closed (an
 *   INFO «correction» naming one section doesn't reopen the others) and are ignored otherwise: a prognosis after a
 *   restriction doesn't re-close the line.
 * - `RESTRICTION` replaces the sections and stops blocking (trains run, with limits): a warning only.
 * - `RESTORATION` with sections reopens those; without sections (or once none are left) it stops blocking.
 * - `ENDED` ends the disruption.
 *
 * Blocking is the `LINE_CLOSED` hint. Replacement buses never switch it to `REPLACEMENT_BUS`: buses come on top of a
 * closed line and go in [Disruption.bus], so `affects()` still blocks the OJP bus legs through the section.
 *
 * Station names are mapped to DIDOK numbers with `incident.stationIds`; [onUnknownStation] gets names that aren't
 * there (`validate.py` should prevent that), and sections using them are dropped.
 */
internal class ScenarioFolder(
    private val incident: Incident,
    private val scenarioId: String,
    private val onUnknownStation: (String) -> Unit = {},
) {
    private var sections = emptyList<Section>()
    private var closed = false
    private var noAlternative = false
    private var ended = false
    private var expectedEnd: Instant? = null
    private var endType: EndType? = null
    private var bus: ReplacementBus? = null

    /** The incident's line in order (DIDOK), attached to sections on it so non-stop trains are matched too. */
    private val line = incident.lineStations.mapNotNull { incident.stationIds[it] }

    fun fold(events: List<ScenarioEvent>): DisruptionData {
        val sorted = events.sortedBy { it.t }
        val updates =
            sorted.mapIndexed { i, event ->
                apply(event)
                DisruptionUpdate(snapshot(version = i + 1, event))
            }
        val timeline = sorted.map { TimelineEntry(incident.id, it.t, it.source, it.kind, it.summary, it.texts()) }
        val stations = incident.stationIds.map { (name, didok) -> StopRef(didok.toString(), name, didok) }
        return DisruptionData(updates, listOf(scenario()), timeline, stations)
    }

    private fun apply(event: ScenarioEvent) {
        applyScope(event.kind, event.sections?.let(::toSections))
        if (!closed) noAlternative = false
        if (event.noAlternative && closed) noAlternative = true
        event.expectedEnd?.let {
            expectedEnd = if (it.type == EndType.UNKNOWN) null else it.time
            endType = it.type
        }
        event.bus?.let { bus = mergeBus(bus, toBus(it)) }
    }

    /** Sections and blocking, following the rules in the class doc. [named] is null when the event names none. */
    private fun applyScope(
        kind: String,
        named: List<Section>?,
    ) {
        when (kind) {
            ENDED -> {
                ended = true
                closed = false
            }
            RESTRICTION -> {
                named?.let { sections = it }
                closed = false
            }
            RESTORATION -> {
                if (named != null) sections = sections.filterNot { s -> named.any { it.sameAs(s) } }
                if (named == null || sections.isEmpty()) closed = false
            }
            in CLOSING ->
                if (named != null) {
                    sections = if (closed) (sections + named).distinctSections() else named
                    closed = true
                }
            else ->
                // PROGNOSIS, INFO, SCOPE_CHANGE…: refine the scope of a closure, never start one.
                if (named != null && closed) sections = (sections + named).distinctSections()
        }
    }

    private fun snapshot(
        version: Int,
        event: ScenarioEvent,
    ) = Disruption(
        id = incident.id,
        version = version,
        publishedAt = event.t,
        status = if (ended) DisruptionStatus.ENDED else DisruptionStatus.ACTIVE,
        affectedStops = sections.flatMap { listOf(it.from, it.to) }.distinct(),
        lines = incident.lines,
        expectedEnd = expectedEnd,
        texts = event.texts().mapValues { (lang, body) -> DisruptionText(incident.title(lang), body) },
        hints =
            listOfNotNull(
                DisruptionHints.LINE_CLOSED.takeIf { closed },
                DisruptionHints.NO_ALTERNATIVE.takeIf { noAlternative },
            ),
        sections = sections,
        expectedEndType = endType,
        bus = bus,
    )

    private fun scenario() =
        Scenario(
            id = scenarioId,
            title = incident.title,
            titles = incident.titles + (SCENARIO_LANGUAGE to incident.title),
            start = incident.replayStart,
            end = incident.replayEnd,
            disruptionIds = listOf(incident.id),
            suggestedFrom = didok(incident.suggestedFrom ?: incident.lineStations.firstOrNull())?.toString(),
            suggestedTo = didok(incident.suggestedTo ?: incident.lineStations.lastOrNull())?.toString(),
            suggestedFromName = incident.suggestedFrom ?: incident.lineStations.firstOrNull(),
            suggestedToName = incident.suggestedTo ?: incident.lineStations.lastOrNull(),
        )

    /** The summary in every language the file has, the original included. */
    private fun ScenarioEvent.texts(): Map<String, String> = summaries + (SCENARIO_LANGUAGE to summary)

    private fun Incident.title(language: String): String = titles[language] ?: title

    private fun didok(name: String?): Int? =
        name?.let { n -> incident.stationIds[n].also { if (it == null) onUnknownStation(n) } }

    private fun toSections(named: List<NamedSection>): List<Section> =
        named.mapNotNull { s ->
            val from = didok(s.from)
            val to = didok(s.to)
            when {
                from == null || to == null -> null
                from in line && to in line -> Section(from, to, line)
                else -> Section(from, to)
            }
        }

    private fun toBus(b: EventBus) =
        ReplacementBus(
            status = b.status,
            from = didok(b.from),
            to = didok(b.to),
            eta = b.eta,
            count = b.count,
            routes = toSections(b.routes),
        )

    private companion object {
        const val ENDED = "ENDED"
        const val RESTRICTION = "RESTRICTION"
        const val RESTORATION = "RESTORATION"

        /** Kinds that close the line when they name sections. */
        val CLOSING = setOf("INTERRUPTION", "DETECTED", "SEVERITY_UP")
    }
}

/** Direction doesn't matter for a section. */
private fun Section.sameAs(other: Section) = setOf(from, to) == setOf(other.from, other.to)

private fun List<Section>.distinctSections(): List<Section> = distinctBy { setOf(it.from, it.to) }

/**
 * `Disruption` holds one bus (a list is requested from the shared contract). A bus event from the same place,
 * or without a place, updates the current bus: newer details win, missing ones are kept, and the status only moves
 * forward (ORDERED → SCHEDULED → PUBLISHED). A bus from another place replaces it only if it says at least as much,
 * so the 4 buses from Palézieux with times and routes aren't swapped for a regional bus with less detail.
 */
internal fun mergeBus(
    current: ReplacementBus?,
    new: ReplacementBus,
): ReplacementBus =
    when {
        current == null -> new
        new.from == null || new.from == current.from ->
            ReplacementBus(
                status = maxOf(current.status, new.status),
                from = current.from ?: new.from,
                to = new.to ?: current.to,
                eta = new.eta ?: current.eta,
                count = new.count ?: current.count,
                routes = new.routes.ifEmpty { current.routes },
            )
        new.detail() >= current.detail() -> new
        else -> current
    }

private fun ReplacementBus.detail(): Int = listOfNotNull(from, to, eta, count).size + routes.size
