package ch.ausweg.shared.ojp

import ch.ausweg.shared.model.Situation
import ch.ausweg.shared.model.ValidityWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class OjpParserSituationTest {
    private val response = OjpParser.stopEvents(OjpSituationFixtures.STOP_EVENTS)

    private fun situation(idEnd: String): Situation = response.situations.single { it.id.endsWith(idEnd) }

    private fun t(value: String) = Instant.parse(value)

    @Test
    fun readsEverySituationOnTheBoard() {
        assertNull(response.error)
        assertEquals(4, response.situations.size)
        assertTrue(response.situations.all { it.planned && it.cause == "constructionWork" })
    }

    @Test
    fun replacementBusIsOnlyInTheTexts() {
        val biel = situation("44fb2fcba2cd-1-2")
        assertEquals(4, biel.version)
        assertEquals("de", biel.language)
        assertEquals("Einschränkung Biel/Bienne - Grenchen Süd", biel.summary)
        assertEquals("Die Linie S20 fällt zwischen Biel/Bienne und Grenchen Süd aus.", biel.consequence)
        assertEquals(
            "Reisende zwischen Biel/Bienne und Grenchen Süd benützen die Ersatzbusse (EV).",
            biel.recommendation,
        )
        assertEquals("Die Einschränkung dauert von 05.11.2026, 03:00 bis 13.12.2026, 03:00.", biel.duration)
        assertTrue(biel.description!!.startsWith("Der Bahnverkehr zwischen Biel/Bienne und Grenchen Süd"))
        assertTrue(biel.lines.isEmpty())
    }

    @Test
    fun stopsAreStationsInListedOrderWithDidokNumbers() {
        val biel = situation("44fb2fcba2cd-1-2")
        assertEquals(
            listOf(
                "Biel/Bienne",
                "Biel Mett",
                "Biel/Bienne Bözingenfeld/Champ",
                "Pieterlen",
                "Lengnau BE",
                "Grenchen Süd",
            ),
            biel.stops.map { it.name },
        )
        assertEquals(8504300, biel.stops.first().didok)
        assertEquals("ch:1:sloid:202", biel.stops.last().ref)
        assertEquals(8500202, biel.stops.last().didok)
    }

    @Test
    fun foreignStopsKeepTheirDidokNumber() {
        val iselle = situation("b347f65103a4")
        assertEquals(listOf(8501609, 8501952, 8501951, 8501950, 8301003), iselle.stops.map { it.didok })
        assertEquals("Domodossola (I)", iselle.stops.last().name)
        assertEquals(listOf("RE1"), iselle.lines)
        assertEquals("Der Bahnverkehr zwischen Iselle di Trasquera und Domodossola ist unterbrochen.", iselle.summary)
    }

    @Test
    fun everyValidityWindowIsKept() {
        val iselle = situation("b347f65103a4")
        assertEquals(
            listOf(
                ValidityWindow(t("2026-11-21T21:30:00Z"), t("2026-11-22T02:30:00Z")),
                ValidityWindow(t("2026-11-22T21:30:00Z"), t("2026-11-23T02:30:00Z")),
            ),
            iselle.validity,
        )
        assertEquals(t("2026-11-21T21:30:00Z"), iselle.validFrom)
        assertEquals(t("2026-11-23T02:30:00Z"), iselle.validTo)
    }

    @Test
    fun recordedAtIsTheLatestTextUpdate() {
        // CreationTime is when the first version was made, weeks earlier.
        assertEquals(t("2026-11-12T12:08:58Z"), situation("44fb2fcba2cd-1-2").recordedAt)
        assertEquals(t("2026-10-29T12:56:02Z"), situation("b347f65103a4").recordedAt)
    }

    @Test
    fun stopsListedPerLineAppearOnce() {
        val bern = situation("f514fcfa856c")
        assertEquals(listOf("S1", "S3"), bern.lines)
        assertEquals(10, bern.stops.size)
        assertEquals(bern.stops.map { it.ref }.distinct(), bern.stops.map { it.ref })
        assertEquals("Bern Wankdorf", bern.stops.first().name)
        assertEquals(8507000, bern.stops.last().didok)
    }

    @Test
    fun languageFollowsTheRequest() {
        val lausanne = situation("b78fe7192078-2")
        assertEquals("fr", lausanne.language)
        assertEquals("Le trafic ferroviaire est restreint entre Lausanne et Genève.", lausanne.summary)
        assertNull(lausanne.consequence)
        assertEquals(8501008, lausanne.stops.last().didok)
        assertEquals(23, lausanne.stops.size)
    }

    @Test
    fun boardWithoutSituations() {
        val none = OjpParser.stopEvents(OjpSituationFixtures.STOP_EVENTS_NONE)
        assertTrue(none.situations.isEmpty())
        assertNull(none.error)
    }

    @Test
    fun tripAnswersUseTheSameSituationReader() {
        val fire = OjpParser.trips(OjpFixtures.TRIPS).situations.single { it.id == "sit-puidoux-fire" }
        assertEquals(2, fire.version)
        assertEquals("fire", fire.cause)
        assertEquals(t("2026-09-25T14:31:08Z"), fire.recordedAt)
        assertEquals(listOf(ValidityWindow(t("2026-09-25T14:31:00Z"), t("2026-09-25T16:45:00Z"))), fire.validity)
        assertTrue(fire.stops.isEmpty())
    }
}
