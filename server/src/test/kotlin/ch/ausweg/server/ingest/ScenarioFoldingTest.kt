package ch.ausweg.server.ingest

import ch.ausweg.server.instant
import ch.ausweg.server.replay.Snapshot
import ch.ausweg.shared.model.BusStatus
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.InfoSource
import ch.ausweg.shared.model.ReplacementBus
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Runs the folding on the real demo scenarios in `data/scenarios`. Times in comments are local (CEST). */
class ScenarioFoldingTest {
    private val data = ScenarioDirSource(Path.of("../data/scenarios")).load()

    private val puidoux = 8504012
    private val vauderens = 8504021
    private val lucens = 8504121
    private val palezieux = 8504014

    private fun at(iso: String): Disruption = Snapshot.at(data.updates, instant(iso)).single()

    private fun Disruption.pairs() = sections.map { it.from to it.to }

    @Test
    fun `both scenarios load with their ids and stations`() {
        assertEquals(
            listOf("puidoux-vauderens_2026-09-25", "renens-morges_2026-09-17"),
            data.scenarios.map { it.id }.sorted(),
        )
        val p = data.scenarios.single { it.id.startsWith("puidoux") }
        assertEquals(listOf("2195005195853"), p.disruptionIds)
        assertEquals("8501120", p.suggestedFrom) // Lausanne
        assertEquals("8504100", p.suggestedTo) // Fribourg/Freiburg
        assertTrue(data.stations.any { it.name == "Palézieux" && it.didok == palezieux })
    }

    @Test
    fun `nothing is closed before the first located event`() {
        val d = at("2026-09-25T14:10:00Z") // 16:10, VIA knows a fire at Palézieux, no section yet
        assertTrue(d.sections.isEmpty())
        assertTrue(d.hints.isEmpty())
    }

    @Test
    fun `public interruption closes both sections until 18_00 approx`() {
        val d = at("2026-09-25T14:30:00Z") // 16:30
        assertEquals(listOf(puidoux to vauderens, puidoux to lucens), d.pairs())
        assertEquals(18, d.sections[0].line.size) // on the Lausanne - Fribourg line: non-stop trains are matched
        assertTrue(d.sections[1].line.isEmpty()) // Lucens is on another line
        assertEquals(listOf(DisruptionHints.LINE_CLOSED), d.hints)
        assertEquals(instant("2026-09-25T16:00:00Z"), d.expectedEnd)
        assertEquals(EndType.APPROX, d.expectedEndType)
        assertEquals(DisruptionStatus.ACTIVE, d.status)
        assertEquals("fr", d.texts.keys.single())
    }

    @Test
    fun `internal bus plan is known at 16_45 and keeps its details once published`() {
        val internal = at("2026-09-25T14:50:00Z").bus
        assertEquals(BusStatus.SCHEDULED, internal?.status)
        assertEquals(palezieux, internal?.from)
        assertEquals(instant("2026-09-25T15:15:00Z"), internal?.eta)
        assertEquals(4, internal?.routes?.size)

        val published = at("2026-09-25T15:05:00Z").bus // after the 16:59 «bus are coming» with no details
        assertEquals(BusStatus.PUBLISHED, published?.status)
        assertEquals(palezieux, published?.from)
        assertEquals(instant("2026-09-25T15:15:00Z"), published?.eta)

        // 17:11 regional bus from Lucens says less: the Palézieux buses stay.
        assertEquals(palezieux, at("2026-09-25T15:20:00Z").bus?.from)
    }

    @Test
    fun `a correction naming one section does not reopen the other`() {
        val d = at("2026-09-25T15:12:00Z") // after 17:11:50 INFO «Puidoux - Lucens»
        assertEquals(setOf(puidoux to vauderens, puidoux to lucens), d.pairs().toSet())
        assertTrue(DisruptionHints.LINE_CLOSED in d.hints)
    }

    @Test
    fun `restoration reopens its sections and the public all-clear stops blocking`() {
        val partly = at("2026-09-25T15:57:00Z") // 17:56 power back Puidoux - Vauderens
        assertEquals(listOf(puidoux to lucens), partly.pairs())
        assertTrue(DisruptionHints.LINE_CLOSED in partly.hints)

        val restored = at("2026-09-25T16:12:00Z") // 18:09:57 public: trains can resume
        assertFalse(DisruptionHints.LINE_CLOSED in restored.hints)

        assertEquals(DisruptionStatus.ENDED, at("2026-09-25T16:20:00Z").status)
    }

    @Test
    fun `renens unknown prognosis clears the end and no-alternative is flagged`() {
        val noAlt = at("2026-09-17T15:45:00Z") // 17:45 internal: no way to Lausanne / Genève, at least 18:30
        assertTrue(DisruptionHints.NO_ALTERNATIVE in noAlt.hints)
        assertEquals(EndType.AT_LEAST, noAlt.expectedEndType)

        val unknown = at("2026-09-17T16:20:00Z") // 18:19 internal: no prognosis
        assertNull(unknown.expectedEnd)
        assertEquals(EndType.UNKNOWN, unknown.expectedEndType)
    }

    @Test
    fun `renens restriction warns only, and the later prognosis does not re-close`() {
        val restriction = at("2026-09-17T19:06:00Z") // 21:05 interruption becomes a restriction
        assertTrue(restriction.hints.isEmpty())
        assertEquals(2, restriction.sections.size)

        val prognosis = at("2026-09-17T19:16:00Z") // 21:15 PROGNOSIS naming Allaman - Morges
        assertTrue(prognosis.hints.isEmpty())
        assertEquals(restriction.pairs(), prognosis.pairs())
        assertEquals(EndType.AT_LEAST, prognosis.expectedEndType)
    }

    @Test
    fun `only closing kinds close the line, and they re-close after a restriction`() {
        val incident =
            Incident(
                id = "X",
                title = "t",
                lineStations = listOf("A", "B", "C"),
                stationIds = mapOf("A" to 1, "B" to 2, "C" to 3),
                replayStart = instant("2026-09-01T08:00:00Z"),
                replayEnd = instant("2026-09-01T12:00:00Z"),
            )

        fun event(
            minute: Int,
            kind: String,
            vararg sections: Pair<String, String>,
        ) = ScenarioEvent(
            t = instant("2026-09-01T08:00:00Z") + minute.minutes,
            source = InfoSource.PUBLIC,
            kind = kind,
            summary = kind,
            sections = sections.map { (a, b) -> NamedSection(a, b) }.ifEmpty { null },
        )
        val versions =
            ScenarioFolder(incident, "x")
                .fold(
                    listOf(
                        event(0, "INFO", "A" to "B"), // names a section, but nothing is closed yet
                        event(1, "INTERRUPTION", "A" to "B"),
                        event(2, "RESTRICTION", "A" to "B"),
                        event(3, "INTERRUPTION", "B" to "C"),
                    ),
                ).updates
                .map { it.disruption }
        assertEquals(listOf(false, true, false, true), versions.map { DisruptionHints.LINE_CLOSED in it.hints })
        assertTrue(versions[0].sections.isEmpty())
        assertEquals(listOf(2 to 3), versions[3].pairs())
    }

    @Test
    fun `timeline has one entry per event with its source`() {
        val puidouxTimeline = data.timeline.filter { it.disruptionId == "2195005195853" }
        assertEquals(data.updates.count { it.disruption.id == "2195005195853" }, puidouxTimeline.size)
        assertEquals(InfoSource.VIA, puidouxTimeline.first().source)
        assertTrue(puidouxTimeline.any { it.source == InfoSource.INTERNAL && it.kind == "REPLACEMENT_BUS" })
    }

    @Test
    fun `bus merge keeps details, moves status forward, and keeps the richer bus`() {
        val rich = ReplacementBus(BusStatus.SCHEDULED, from = 1, eta = instant("2026-09-25T15:15:00Z"), count = 4)
        assertEquals(rich.copy(status = BusStatus.PUBLISHED), mergeBus(rich, ReplacementBus(BusStatus.PUBLISHED)))
        assertEquals(rich, mergeBus(rich, ReplacementBus(BusStatus.ORDERED, from = 1)))
        assertEquals(rich, mergeBus(rich, ReplacementBus(BusStatus.SCHEDULED, from = 2)))
        val richer = ReplacementBus(BusStatus.SCHEDULED, from = 2, to = 3, eta = rich.eta, count = 10)
        assertEquals(richer, mergeBus(rich, richer))
    }
}
