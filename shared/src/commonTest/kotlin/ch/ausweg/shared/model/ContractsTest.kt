package ch.ausweg.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ContractsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun disruptionWithoutNewFieldsStillDecodes() {
        val old =
            """{"id":"d1","version":1,"publishedAt":"2026-09-25T14:31:08Z","status":"ACTIVE",""" +
                """"affectedStops":[8504012,8504021],"hints":["LINE_CLOSED"]}"""
        val d = json.decodeFromString<Disruption>(old)
        assertEquals(listOf(8504012, 8504021), d.affectedStops)
        assertTrue(d.sections.isEmpty())
        assertNull(d.expectedEndType)
        assertNull(d.bus)
    }

    @Test
    fun disruptionWithSectionsAndBusRoundTrips() {
        val d =
            Disruption(
                id = "2195005195853",
                version = 2,
                publishedAt = Instant.parse("2026-09-25T14:27:22Z"),
                status = DisruptionStatus.ACTIVE,
                expectedEnd = Instant.parse("2026-09-25T16:00:00Z"),
                expectedEndType = EndType.APPROX,
                hints = listOf(DisruptionHints.LINE_CLOSED),
                sections = listOf(Section(8504012, 8504021), Section(8504012, 8504121)),
                bus = ReplacementBus(BusStatus.SCHEDULED, from = 8504014, eta = Instant.parse("2026-09-25T15:15:00Z")),
            )
        assertEquals(d, json.decodeFromString<Disruption>(json.encodeToString(d)))
    }

    @Test
    fun adviceRoundTripsAsSealedType() {
        val all: List<Advice> =
            listOf(
                Advice.Ok(),
                Advice.Wait(null, EndType.UNKNOWN, AdviceReason.NO_ALTERNATIVE, TripImpact(cancelledLegs = listOf(0))),
                Advice.Detour(emptyList(), AdviceReason.OPEN_ENDED, TripImpact()),
                Advice.TakeBus(ReplacementBus(BusStatus.PUBLISHED), AdviceReason.BUS_AVAILABLE, TripImpact()),
            )
        all.forEach { assertEquals(it, json.decodeFromString<Advice>(json.encodeToString(it))) }
    }

    @Test
    fun impactIsBlockedByBlockingDisruptionOrCancelledLeg() {
        assertFalse(TripImpact(skippedStops = listOf(8504014)).isBlocked)
        assertTrue(TripImpact(cancelledLegs = listOf(1)).isBlocked)
    }
}
