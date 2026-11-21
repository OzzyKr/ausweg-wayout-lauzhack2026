package ch.ausweg.shared.routing

import ch.ausweg.shared.disruption.Fx
import ch.ausweg.shared.disruption.Fx.FRIBOURG
import ch.ausweg.shared.disruption.Fx.LAUSANNE
import ch.ausweg.shared.disruption.Fx.PALEZIEUX
import ch.ausweg.shared.disruption.Fx.PUIDOUX
import ch.ausweg.shared.disruption.Fx.ROMONT
import ch.ausweg.shared.disruption.Fx.VAUDERENS
import ch.ausweg.shared.disruption.Fx.fire
import ch.ausweg.shared.disruption.Fx.ic1
import ch.ausweg.shared.disruption.Fx.leg
import ch.ausweg.shared.disruption.Fx.t
import ch.ausweg.shared.disruption.Fx.trip
import ch.ausweg.shared.disruption.Fx.viaYverdon
import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.BusStatus
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.ReplacementBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/** Clock 14:30Z; the fire closes Puidoux – Vauderens until 16:00Z «environ». */
class OptionsTest {
    private val now = t("14:30")

    @Test
    fun blockedUsualTripGivesTheDetourAsPick() {
        val detour = viaYverdon("14:40", "15:50")
        val v = planOptions(listOf(detour, ic1), listOf(fire), now)
        assertEquals(ic1, v.usual)
        assertIs<Advice.Detour>(v.advice)
        assertEquals(Pick.Go(detour), v.pick)
        assertEquals(listOf(fire), v.blockedBy)
        assertEquals(listOf(TripOption(ic1, blocked = true)), v.others)
    }

    @Test
    fun theClockMovingOnDropsDetoursThatLeft() {
        // Planned at 14:30; at 14:50 the 14:40 detour has left, so only the 15:00 one can be the pick.
        val early = viaYverdon("14:40", "15:50")
        val later = viaYverdon("15:00", "16:10")
        val v = planOptions(listOf(early, later, ic1), listOf(fire), t("14:50"))
        assertEquals(Pick.Go(later), v.pick)
    }

    @Test
    fun nothingBlockedPicksTheFirstTrip() {
        val detour = viaYverdon("14:40", "15:50")
        val v = planOptions(listOf(ic1, detour), emptyList(), now)
        assertEquals(Pick.Go(ic1), v.pick)
        assertEquals(emptyList(), v.blockedBy)
        assertEquals(listOf(TripOption(detour, blocked = false)), v.others)
    }

    @Test
    fun waitingWinsOverASlowDetour() {
        val slow = viaYverdon("14:40", "17:00")
        val v = planOptions(listOf(slow, ic1), listOf(fire), now)
        assertEquals(Pick.Wait(ic1, t("16:00"), EndType.APPROX), v.pick)
        assertEquals(listOf(TripOption(slow, blocked = false)), v.others)
    }

    @Test
    fun waitingShowsTheFirstTrainAfterTheReopeningNotTheBlockedOne() {
        // The line reopens at 16:00: the 16:15 IC1 is the one to wait for, not the blocked 14:45 one.
        val slow = viaYverdon("14:40", "17:00")
        val next =
            trip(
                "ic1-next",
                "16:15",
                "17:00",
                leg(LAUSANNE, PUIDOUX, PALEZIEUX, VAUDERENS, ROMONT, FRIBOURG, dep = "16:15"),
            )
        val v = planOptions(listOf(slow, ic1, next), listOf(fire), now)
        assertEquals(Pick.Wait(next, t("16:00"), EndType.APPROX), v.pick)
        assertEquals(listOf(TripOption(slow, blocked = false), TripOption(ic1, blocked = true)), v.others)
    }

    @Test
    fun aBusIsPickedWhenNothingElseWorks() {
        val bus = ReplacementBus(BusStatus.SCHEDULED, from = Fx.PALEZIEUX, eta = t("15:15"))
        val v = planOptions(listOf(ic1), listOf(fire.copy(expectedEnd = null, bus = bus)), now)
        assertEquals(Pick.Bus(bus, ic1), v.pick)
        assertEquals(ic1, v.pick?.trip)
    }

    @Test
    fun noTripsNoOptions() {
        val v = planOptions(emptyList(), listOf(fire), now)
        assertNull(v.usual)
        assertNull(v.pick)
    }

    @Test
    fun durationIsDoorToDoor() {
        assertEquals(45.minutes, ic1.duration)
    }
}
