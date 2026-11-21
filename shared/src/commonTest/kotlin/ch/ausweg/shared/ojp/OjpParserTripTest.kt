package ch.ausweg.shared.ojp

import ch.ausweg.shared.model.LegMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class OjpParserTripTest {
    private val response = OjpParser.trips(OjpFixtures.TRIPS)

    private fun trip(id: String) = response.trips.single { it.id == id }

    @Test
    fun readsAllTripsInOrderWithoutError() {
        assertEquals(listOf("T-direct", "T-bus", "T-cancelled"), response.trips.map { it.id })
        assertNull(response.error)
    }

    @Test
    fun directTrainLeg() {
        val trip = trip("T-direct")
        assertEquals(Instant.parse("2026-09-25T15:20:00Z"), trip.departure)
        assertEquals(Instant.parse("2026-09-25T16:06:00Z"), trip.arrival)
        assertEquals(0, trip.transfers)

        val leg = trip.legs.single()
        assertEquals(LegMode.RAIL, leg.mode)
        assertEquals("IR15", leg.line)
        assertEquals("Luzern", leg.direction)
        assertFalse(leg.cancelled)

        assertEquals("Lausanne", leg.from.stop.name)
        assertEquals("ch:1:sloid:1120:7:7", leg.from.stop.ref)
        assertEquals(8501120, leg.from.stop.didok)
        assertEquals(8504100, leg.to.stop.didok)
        assertEquals("2", leg.to.platform)
    }

    @Test
    fun estimatedPlatformAndTimesWinWhenPresent() {
        val board = trip("T-direct").legs.single().from
        assertEquals("8", board.platform)
        assertEquals(Instant.parse("2026-09-25T15:20:00Z"), board.departure!!.timetabled)
        assertEquals(Instant.parse("2026-09-25T15:23:00Z"), board.departure!!.estimated)
        assertNull(board.arrival)
    }

    @Test
    fun intermediateStopsIncludingSkippedOnes() {
        val intermediate = trip("T-direct").legs.single().intermediate
        // Puidoux and Romont FR (official names and DIDOK numbers from data/scenarios stationIds).
        assertEquals(listOf(8504012, 8504023), intermediate.map { it.stop.didok })
        assertTrue(intermediate[0].notServiced)
        assertFalse(intermediate[1].notServiced)
        assertEquals(Instant.parse("2026-09-25T15:45:00Z"), intermediate[1].arrival!!.timetabled)
    }

    @Test
    fun replacementBusTripWithWalkTransfer() {
        val trip = trip("T-bus")
        assertEquals(listOf(LegMode.RAIL, LegMode.WALK, LegMode.BUS), trip.legs.map { it.mode })
        // No <Transfers> in this trip: counted from the rides.
        assertEquals(1, trip.transfers)

        val walk = trip.legs[1]
        assertNull(walk.line)
        assertEquals("ch:1:sloid:4012:2:2", walk.from.stop.ref)
        assertEquals("ch:1:sloid:4012", walk.to.stop.ref)
        assertEquals(8504012, walk.to.stop.didok)
        assertEquals("Puidoux, gare (bus de remplacement)", walk.to.stop.name)
        assertEquals(Instant.parse("2026-09-25T15:38:00Z"), walk.from.departure!!.timetabled)
        assertEquals(Instant.parse("2026-09-25T15:42:00Z"), walk.to.arrival!!.timetabled)

        assertEquals("EV", trip.legs[2].line)
    }

    @Test
    fun cancelledLegAndNumericStopRefs() {
        val leg = trip("T-cancelled").legs.single()
        assertTrue(leg.cancelled)
        assertEquals(8501120, leg.from.stop.didok)
        assertNull(leg.direction)
    }

    @Test
    fun situationsAndLegReferences() {
        assertEquals(listOf("sit-puidoux-fire"), trip("T-direct").legs.single().situationIds)

        val fire = response.situations.single { it.id == "sit-puidoux-fire" }
        assertEquals("Interrupted service: Puidoux - Vauderens", fire.summary)
        assertEquals("No trains between Puidoux and Vauderens & Lucens because of a fire.", fire.description)
        assertEquals("en", fire.language)
        assertEquals(Instant.parse("2026-09-25T14:31:00Z"), fire.validFrom)
        assertEquals(Instant.parse("2026-09-25T16:45:00Z"), fire.validTo)

        val lift = response.situations.single { it.id == "sit-lift-fribourg" }
        assertEquals("Ascenseur hors service", lift.summary)
        assertEquals("fr", lift.language)
        assertNull(lift.validTo)
    }

    @Test
    fun noTripFoundIsAnErrorCodeNotAnException() {
        val notFound = OjpParser.trips(OjpFixtures.TRIPS_NOT_FOUND)
        assertTrue(notFound.trips.isEmpty())
        assertEquals("TRIP_NOTRIPFOUND", notFound.error)
    }

    @Test
    fun malformedResponsesThrow() {
        assertFailsWith<OjpParseException> { OjpParser.trips("not xml at all") }
        assertFailsWith<OjpParseException> { OjpParser.trips("<OJP><OJPResponse>") }
        assertFailsWith<OjpParseException> { OjpParser.trips("<OJP/>") }
        assertFailsWith<OjpParseException> { OjpParser.trips(OjpFixtures.LOCATIONS) }
    }

    @Test
    fun missingRequiredElementThrows() {
        val noStart = OjpFixtures.TRIPS.replace("<StartTime>2026-09-25T15:20:00Z</StartTime>", "")
        assertFailsWith<OjpParseException> { OjpParser.trips(noStart) }
    }
}
