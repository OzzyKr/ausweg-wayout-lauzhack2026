package ch.ausweg.shared.tips

import ch.ausweg.shared.disruption.Fx
import ch.ausweg.shared.disruption.Fx.t
import ch.ausweg.shared.model.BusStatus
import ch.ausweg.shared.model.CallTime
import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.Leg
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.ReplacementBus
import ch.ausweg.shared.model.RouteLeg
import ch.ausweg.shared.model.StopCall
import ch.ausweg.shared.model.StopRef
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.TipKind
import ch.ausweg.shared.model.Trip
import ch.ausweg.shared.routing.Stage
import ch.ausweg.shared.routing.progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegStripTest {
    private fun stop(
        name: String,
        didok: Int,
    ) = StopRef(didok.toString(), name, didok)

    private val lausanne = stop("Lausanne", Fx.LAUSANNE)
    private val yverdon = stop("Yverdon-les-Bains", Fx.YVERDON)
    private val fribourg = stop("Fribourg/Freiburg", Fx.FRIBOURG)

    private fun call(
        s: StopRef,
        time: String,
        platform: String? = null,
        estimated: String? = null,
    ) = StopCall(
        s,
        platform,
        arrival = CallTime(t(time), estimated?.let(::t)),
        departure = CallTime(t(time), estimated?.let(::t)),
    )

    /** IC5 Lausanne pl. 7 → Yverdon pl. 2, walk, S30 Yverdon pl. 3 → Fribourg (2 min late). */
    private val trip =
        Trip(
            "t",
            t("14:40"),
            t("15:52"),
            1,
            listOf(
                Leg(LegMode.RAIL, call(lausanne, "14:40", "7"), call(yverdon, "15:02", "2"), line = "IC5"),
                Leg(LegMode.WALK, call(yverdon, "15:02"), call(yverdon, "15:05")),
                Leg(
                    LegMode.RAIL,
                    call(yverdon, "15:08", "3"),
                    call(fribourg, "15:50", estimated = "15:52"),
                    line = "S30",
                ),
            ),
        )

    private fun note(
        id: String,
        legIndex: Int,
        at: String,
    ) = Tip(id, TipKind.DISRUPTION, "note $id", t(at), routeId = "r", legIndex = legIndex)

    @Test
    fun walksAreFoldedIntoTheChange() {
        val strip = legStrip(trip)
        assertEquals(listOf("IC5", "S30"), strip.legs.map { it.label })
        val change = strip.changes.single()
        assertEquals(yverdon, change.station)
        assertEquals("2", change.arrivalPlatform)
        assertEquals("3", change.departurePlatform)
        assertEquals(6, change.minutes)
        assertEquals(2, strip.legs[1].delayMinutes)
        assertEquals(22, strip.legs[0].minutes)
        assertEquals(t("15:52"), strip.arrival)
    }

    @Test
    fun notesAreNumberedInRouteOrder() {
        val route =
            CommunityRoute(
                "r",
                lausanne,
                fribourg,
                listOf(
                    RouteLeg(lausanne, yverdon, "IC5"),
                    RouteLeg(yverdon, stop("Payerne", 8504134), "S30"),
                    RouteLeg(stop("Payerne", 8504134), fribourg, "S40"),
                ),
                t("12:00"),
            )
        val strip = legStrip(route, listOf(note("b", 1, "12:05"), note("a", 0, "12:10")))
        assertEquals(StripKind.TRAIN, strip.legs.first().kind)
        assertEquals(listOf(1), strip.changes[0].notes.map { it.number })
        assertEquals(
            "a",
            strip.changes[0]
                .notes
                .single()
                .tip.id,
        )
        assertEquals(listOf(2), strip.changes[1].notes.map { it.number })
        assertNull(strip.changes[0].minutes)
        assertEquals(listOf("a", "b"), strip.notes.map { it.tip.id })
    }

    @Test
    fun notesOffTheChangesAreLeftOutBeforeNumbering() {
        // A note after the last ride (legIndex 1 of 2 rides) is never drawn, so it mustn't take number 1.
        val strip = legStrip(trip, listOf(note("last", 1, "12:00"), note("first", 0, "12:10")))
        assertEquals(listOf(1), strip.notes.map { it.number })
        assertEquals(listOf("first"), strip.notes.map { it.tip.id })
    }

    @Test
    fun stripLegsKeepTheirMode() {
        val metro =
            trip.copy(
                legs = listOf(Leg(LegMode.METRO, call(lausanne, "14:40"), call(yverdon, "14:50"), line = "M2")),
            )
        val leg = legStrip(metro).legs.single()
        assertEquals(StripKind.OTHER, leg.kind)
        assertEquals(LegMode.METRO, leg.mode)
    }

    @Test
    fun busStripUsesTripStopNamesWhenItCan() {
        val bus = ReplacementBus(BusStatus.SCHEDULED, from = Fx.LAUSANNE, to = Fx.PUIDOUX, eta = t("15:15"))
        val strip = busStrip(bus, trip)
        val leg = strip.legs.single()
        assertEquals(StripKind.REPLACEMENT_BUS, leg.kind)
        assertEquals("Lausanne", leg.from.name)
        assertEquals("", leg.to.name)
        assertEquals(t("15:15"), leg.departure)
    }

    @Test
    fun progressFollowsTheClock() {
        val strip = legStrip(trip)
        assertEquals(Stage.BEFORE_DEPARTURE, progress(strip, t("14:30")).stage)
        val riding = progress(strip, t("14:50"))
        assertEquals(Stage.ON_THE_WAY, riding.stage)
        assertEquals(0, riding.currentLeg)
        assertEquals(yverdon, riding.nextChange?.station)
        val last = progress(strip, t("15:10"))
        assertEquals(1, last.currentLeg)
        assertNull(last.nextChange)
        assertEquals(2, last.delayMinutes)
        assertEquals(Stage.ARRIVED, progress(strip, t("16:00")).stage)
    }

    @Test
    fun routesWithoutTimesHaveUnknownProgress() {
        val strip =
            stripOf(
                listOf(
                    StripLeg("IC5", StripKind.TRAIN, lausanne, yverdon),
                    StripLeg("S30", StripKind.TRAIN, yverdon, fribourg),
                ),
                emptyList(),
            )
        val p = progress(strip, t("14:00"))
        assertEquals(Stage.UNKNOWN, p.stage)
        assertEquals(yverdon, p.nextChange?.station)
        assertEquals(Stage.UNKNOWN, progress(stripOf(emptyList(), emptyList()), t("14:00")).stage)
    }
}
