package ch.ausweg.shared.disruption

import ch.ausweg.shared.model.BusStatus
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.Section
import ch.ausweg.shared.model.Situation
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.ValidityWindow
import ch.ausweg.shared.ojp.OjpParser
import ch.ausweg.shared.ojp.OjpSituationFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LiveSituationsTest {
    private val recorded = OjpParser.stopEvents(OjpSituationFixtures.STOP_EVENTS).situations

    private fun real(idEnd: String): Situation = recorded.single { it.id.endsWith(idEnd) }

    private fun t(value: String) = Instant.parse(value)

    /** Saturday evening, when the fixtures were recorded: Biel, Bern and Lausanne are on, Iselle starts at 21:30. */
    private val evening = t("2026-11-21T21:00:00Z")
    private val night = t("2026-11-21T23:00:00Z")

    private val biel = 8504300
    private val grenchenSud = 8500202
    private val iselle = 8501952
    private val domodossola = 8301003

    private fun situation(
        summary: String,
        stops: List<Pair<String, Int>> = listOf("Aarau" to 8502113, "Olten" to 8500218),
        validity: List<ValidityWindow> = emptyList(),
        recommendation: String? = null,
        language: String = "de",
    ) = Situation(
        id = "s1",
        summary = summary,
        recommendation = recommendation,
        language = language,
        validity = validity,
        stops = stops.map { (name, didok) -> StopRef("ch:1:sloid:${didok - 8_500_000}", name, didok) },
    )

    @Test
    fun replacementBusFromTheRecommendation() {
        val d = assertNotNull(real("44fb2fcba2cd-1-2").toDisruption(evening))
        assertEquals("ojp:ch:1:sstid:100001:ee15f8b7-d5c1-41e2-9661-44fb2fcba2cd-1-2", d.id)
        assertEquals(4, d.version)
        assertEquals(DisruptionStatus.ACTIVE, d.status)
        assertEquals(listOf(DisruptionHints.REPLACEMENT_BUS), d.hints)
        assertEquals(listOf(Section(biel, grenchenSud)), d.sections)
        assertEquals(BusStatus.PUBLISHED, d.bus?.status)
        assertEquals(biel, d.bus?.from)
        assertEquals(grenchenSud, d.bus?.to)
        assertEquals(t("2026-11-12T12:08:58Z"), d.publishedAt)
        assertEquals("constructionWork", d.reasonCode)
    }

    @Test
    fun unknownDurationInTheTextMeansNoExpectedEnd() {
        // Valid until 13 Dec, but the text says «Die Dauer der Einschränkung ist unbestimmt».
        val d = assertNotNull(real("44fb2fcba2cd-1-2").toDisruption(evening))
        assertNull(d.expectedEnd)
        assertEquals(EndType.UNKNOWN, d.expectedEndType)
    }

    @Test
    fun interruptionClosesTheLineDuringItsNightWindow() {
        val situation = real("b347f65103a4")
        assertNull(situation.toDisruption(evening), "not on before 21:30")

        val d = assertNotNull(situation.toDisruption(night))
        assertEquals(listOf(DisruptionHints.LINE_CLOSED), d.hints)
        assertEquals(listOf(Section(iselle, domodossola)), d.sections)
        assertEquals(t("2026-11-22T02:30:00Z"), d.expectedEnd)
        assertNull(d.bus)
        assertNull(situation.toDisruption(t("2026-11-22T12:00:00Z")), "off between the two nights")
        assertEquals(DisruptionStatus.ENDED, situation.toDisruption(t("2026-11-23T03:00:00Z"))?.status)
    }

    @Test
    fun restrictionIsAWarningOnTheNamedSection() {
        val bern = assertNotNull(real("f514fcfa856c").toDisruption(evening))
        assertTrue(bern.hints.isEmpty())
        assertEquals(listOf(Section(8507000, 8507003)), bern.sections, "Bern – Gümligen, not the whole S1 list")
        assertEquals(listOf("S1", "S3"), bern.lines)
        assertEquals(t("2026-11-22T05:00:00Z"), bern.expectedEnd)
        assertNull(bern.expectedEndType)
    }

    @Test
    fun frenchTextsStayFrench() {
        val d = assertNotNull(real("b78fe7192078-2").toDisruption(evening))
        assertEquals(listOf(Section(8501120, 8501008)), d.sections)
        val fr = assertNotNull(d.texts["fr"])
        assertEquals("Le trafic ferroviaire est restreint entre Lausanne et Genève.", fr.title)
        assertTrue(fr.body.startsWith("La ligne RE 33 est concernée."))
        assertEquals(setOf("fr"), d.texts.keys)
    }

    @Test
    fun closedKeywordsInEveryLanguage() {
        listOf(
            "de" to "Der Bahnverkehr zwischen Aarau und Olten ist unterbrochen.",
            "de" to "Zwischen Aarau und Olten verkehren keine Züge.",
            "fr" to "Le trafic ferroviaire est interrompu entre Aarau et Olten.",
            "it" to "Il traffico ferroviario tra Aarau e Olten è interrotto.",
            "en" to "Rail traffic between Aarau and Olten is interrupted.",
        ).forEach { (lang, text) ->
            val d = assertNotNull(situation(text, language = lang).toDisruption(evening), text)
            assertEquals(listOf(DisruptionHints.LINE_CLOSED), d.hints, text)
            assertEquals(listOf(Section(8502113, 8500218)), d.sections, text)
        }
    }

    @Test
    fun busAndNoAlternativeKeywords() {
        listOf("Bahnersatz mit Bussen", "Bus de remplacement", "Bus sostitutivi", "Replacement buses run").forEach {
            assertEquals(listOf(DisruptionHints.REPLACEMENT_BUS), situation(it).toDisruption(evening)?.hints, it)
        }
        val none = situation("Der Bahnverkehr ist unterbrochen.", recommendation = "Es gibt keine Alternative.")
        assertEquals(
            listOf(DisruptionHints.LINE_CLOSED, DisruptionHints.NO_ALTERNATIVE),
            none.toDisruption(evening)?.hints,
        )
    }

    @Test
    fun aLongerStationNameIsNotItsPrefix() {
        val s =
            situation(
                "Le trafic ferroviaire est restreint entre Lausanne et Genève-Aéroport.",
                stops = listOf("Lausanne" to 8501120, "Genève" to 8501008),
                language = "fr",
            )
        val d = assertNotNull(s.toDisruption(evening))
        assertTrue(d.sections.isEmpty(), "Genève-Aéroport isn't among the stops")
        assertEquals(listOf(8501120, 8501008), d.affectedStops)
    }

    @Test
    fun withoutStationNumbersThereIsNothingToMatch() {
        val s = Situation("s1", summary = "Information", stops = listOf(StopRef("de:08212:90", "Karlsruhe")))
        assertNull(s.toDisruption(evening))
    }

    @Test
    fun noValidityMeansActiveWithOpenEnd() {
        val d = assertNotNull(situation("Der Bahnverkehr ist eingeschränkt.").toDisruption(evening))
        assertEquals(DisruptionStatus.ACTIVE, d.status)
        assertNull(d.expectedEnd)
        assertEquals(evening, d.publishedAt, "no RecordedAtTime: published now")
    }

    @Test
    fun liveBusBlocksTrainsThroughTheSectionButNotTheBusItself() {
        val d = assertNotNull(real("44fb2fcba2cd-1-2").toDisruption(evening))
        val train = Fx.trip("rail", "15:00", "15:20", Fx.leg(biel, 8504419, grenchenSud, dep = "15:00", line = "S20"))
        val bus =
            Fx.trip("bus", "15:00", "15:30", Fx.leg(biel, grenchenSud, dep = "15:00", mode = LegMode.BUS, line = "EV"))
        assertTrue(affects(train, listOf(d)).isBlocked)
        assertFalse(affects(bus, listOf(d)).isBlocked)
    }

    @Test
    fun liveRestrictionWarnsButDoesNotBlock() {
        val d = assertNotNull(real("b78fe7192078-2").toDisruption(evening))
        val ir = Fx.trip("ir", "15:00", "15:40", Fx.leg(8501120, 8501030, 8501008, dep = "15:00", line = "IR90"))
        val impact = affects(ir, listOf(d))
        assertFalse(impact.isBlocked)
        assertEquals(listOf(d.id), impact.warnings.map { it.id })
    }
}
