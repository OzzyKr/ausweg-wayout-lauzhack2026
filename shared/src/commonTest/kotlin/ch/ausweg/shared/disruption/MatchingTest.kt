package ch.ausweg.shared.disruption

import ch.ausweg.shared.disruption.Fx.FRIBOURG
import ch.ausweg.shared.disruption.Fx.LAUSANNE
import ch.ausweg.shared.disruption.Fx.PALEZIEUX
import ch.ausweg.shared.disruption.Fx.PUIDOUX
import ch.ausweg.shared.disruption.Fx.ROMONT
import ch.ausweg.shared.disruption.Fx.VAUDERENS
import ch.ausweg.shared.disruption.Fx.call
import ch.ausweg.shared.disruption.Fx.fire
import ch.ausweg.shared.disruption.Fx.leg
import ch.ausweg.shared.disruption.Fx.trip
import ch.ausweg.shared.model.CallTime
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.TripImpact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchingTest {
    @Test
    fun tripThroughClosedSectionIsBlocked() {
        val impact = affects(Fx.ic1, listOf(fire))
        assertEquals(listOf(fire), impact.blocking)
        assertTrue(impact.warnings.isEmpty())
        assertTrue(impact.isBlocked)
    }

    @Test
    fun sectionMatchesInEitherDirection() {
        val back =
            trip(
                "back",
                "14:45",
                "15:30",
                leg(FRIBOURG, ROMONT, VAUDERENS, PALEZIEUX, PUIDOUX, LAUSANNE, dep = "14:45"),
            )
        assertTrue(affects(back, listOf(fire)).isBlocked)
    }

    @Test
    fun touchingOneEndOfSectionIsNotAHit() {
        val toPuidoux = trip("s", "14:45", "15:00", leg(LAUSANNE, PUIDOUX, dep = "14:45", line = "S7"))
        assertEquals(TripImpact(), affects(toPuidoux, listOf(fire)))
    }

    @Test
    fun endedDisruptionIsIgnored() {
        assertFalse(affects(Fx.ic1, listOf(fire.copy(status = DisruptionStatus.ENDED))).isBlocked)
    }

    @Test
    fun legDepartingAtOrAfterExpectedEndIsNotHit() {
        val atEnd = trip("late", "16:00", "16:45", leg(LAUSANNE, PUIDOUX, VAUDERENS, FRIBOURG, dep = "16:00"))
        val justBefore = trip("early", "15:59", "16:44", leg(LAUSANNE, PUIDOUX, VAUDERENS, FRIBOURG, dep = "15:59"))
        assertFalse(affects(atEnd, listOf(fire)).isBlocked)
        assertTrue(affects(justBefore, listOf(fire)).isBlocked)
    }

    @Test
    fun estimatedDepartureWinsOverTimetabled() {
        val delayed = leg(LAUSANNE, PUIDOUX, VAUDERENS, FRIBOURG, dep = "15:50")
        val late = delayed.copy(from = delayed.from.copy(departure = CallTime(Fx.t("15:50"), Fx.t("16:05"))))
        assertFalse(affects(trip("d", "15:50", "16:35", late), listOf(fire)).isBlocked)
    }

    @Test
    fun replacementBusBlocksRailButNotTheBusItself() {
        val bus = fire.copy(hints = listOf(DisruptionHints.REPLACEMENT_BUS))
        assertTrue(affects(Fx.ic1, listOf(bus)).isBlocked)

        val ev =
            trip(
                "ev",
                "14:45",
                "15:40",
                leg(PUIDOUX, PALEZIEUX, VAUDERENS, dep = "14:45", mode = LegMode.BUS, line = "EV"),
            )
        val impact = affects(ev, listOf(bus))
        assertFalse(impact.isBlocked)
        assertEquals(listOf(bus), impact.warnings)
    }

    @Test
    fun lineClosedBlocksBusLegsToo() {
        val bus = trip("b", "14:45", "15:40", leg(PUIDOUX, VAUDERENS, dep = "14:45", mode = LegMode.BUS, line = "381"))
        assertTrue(affects(bus, listOf(fire)).isBlocked)
    }

    @Test
    fun walkLegsAreSkipped() {
        val walk = trip("w", "14:45", "16:45", leg(PUIDOUX, VAUDERENS, dep = "14:45", mode = LegMode.WALK, line = null))
        assertEquals(TripImpact(), affects(walk, listOf(fire)))
    }

    @Test
    fun stopsWithoutDidokAreSkipped() {
        val l = leg(LAUSANNE, PUIDOUX, VAUDERENS, FRIBOURG, dep = "14:45")
        val noDidok = l.copy(intermediate = listOf(l.intermediate[0], call(null)))
        assertFalse(affects(trip("n", "14:45", "15:30", noDidok), listOf(fire)).isBlocked)
    }

    @Test
    fun flatAffectedStopsNeedTwoOnThePath() {
        val flat = fire.copy(sections = emptyList(), affectedStops = listOf(PUIDOUX, VAUDERENS))
        val toPuidoux = trip("s", "14:45", "15:00", leg(LAUSANNE, PUIDOUX, dep = "14:45", line = "S7"))
        assertTrue(affects(Fx.ic1, listOf(flat)).isBlocked)
        assertFalse(affects(toPuidoux, listOf(flat)).isBlocked)
    }

    @Test
    fun singleAffectedStopIsEnough() {
        val oneStop = fire.copy(sections = emptyList(), affectedStops = listOf(PALEZIEUX))
        assertTrue(affects(Fx.ic1, listOf(oneStop)).isBlocked)
    }

    @Test
    fun nonBlockingHintIsOnlyAWarning() {
        val info = fire.copy(hints = listOf(DisruptionHints.NO_ALTERNATIVE))
        val impact = affects(Fx.ic1, listOf(info))
        assertFalse(impact.isBlocked)
        assertEquals(listOf(info), impact.warnings)
    }

    @Test
    fun disruptionHittingSeveralLegsIsListedOnce() {
        val twoLegs =
            trip(
                "two",
                "14:45",
                "15:40",
                leg(LAUSANNE, PUIDOUX, PALEZIEUX, dep = "14:45", line = "S7"),
                leg(PALEZIEUX, PUIDOUX, VAUDERENS, dep = "15:00", line = "S9"),
                leg(VAUDERENS, PUIDOUX, ROMONT, dep = "15:20", line = "S9"),
            )
        assertEquals(listOf(fire), affects(twoLegs, listOf(fire)).blocking)
    }

    @Test
    fun cancelledLegsAndSkippedStops() {
        val base = leg(LAUSANNE, PALEZIEUX, ROMONT, FRIBOURG, dep = "14:45")
        val cancelled = base.copy(cancelled = true)
        val skipsBoarding = base.copy(from = base.from.copy(notServiced = true))
        val skipsMiddle = base.copy(intermediate = listOf(call(PALEZIEUX, notServiced = true), call(ROMONT)))

        assertEquals(listOf(0), affects(trip("c", "14:45", "15:30", cancelled), emptyList()).cancelledLegs)
        val feeder = leg(LAUSANNE, PUIDOUX, dep = "14:30", line = "S7")
        assertEquals(listOf(1), affects(trip("c", "14:30", "15:30", feeder, skipsBoarding), emptyList()).cancelledLegs)

        val middle = affects(trip("m", "14:45", "15:30", skipsMiddle), emptyList())
        assertFalse(middle.isBlocked)
        assertEquals(listOf(PALEZIEUX), middle.skippedStops)
    }
}
