package ch.ausweg.shared.disruption

import ch.ausweg.shared.disruption.Fx.fire
import ch.ausweg.shared.disruption.Fx.ic1
import ch.ausweg.shared.disruption.Fx.t
import ch.ausweg.shared.disruption.Fx.viaYverdon
import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.BusStatus
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.ReplacementBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteUpdatesTest {
    private val now = t("14:30")
    private val detour = viaYverdon("14:40", "15:50")
    private val watched = setOf(fire.id)

    private fun adviceFor(vararg ds: ch.ausweg.shared.model.Disruption) = advise(ic1, ds.toList(), now, listOf(detour))

    @Test
    fun noChangeNoBanner() {
        val a = adviceFor(fire)
        assertNull(
            routeUpdate(RouteCheck(a, listOf(fire)), RouteCheck(a, listOf(fire.copy(version = 3))), watched, now),
        )
    }

    @Test
    fun firstAdviceGivesNoBanner() {
        assertNull(
            routeUpdate(RouteCheck(null, emptyList()), RouteCheck(adviceFor(fire), listOf(fire)), emptySet(), now),
        )
    }

    @Test
    fun okTripThatGetsBlocked() {
        val u =
            routeUpdate(
                RouteCheck(adviceFor(), emptyList()),
                RouteCheck(adviceFor(fire), listOf(fire)),
                emptySet(),
                now,
            )
        assertEquals(UpdateKind.BLOCKED, u?.kind)
        assertEquals(fire, u?.disruption)
    }

    @Test
    fun blockedTripThatClears() {
        val ended = fire.copy(status = DisruptionStatus.ENDED)
        val u =
            routeUpdate(
                RouteCheck(adviceFor(fire), listOf(fire)),
                RouteCheck(adviceFor(ended), listOf(ended)),
                watched,
                now,
            )
        assertEquals(UpdateKind.RESOLVED, u?.kind)
        assertEquals(fire, u?.disruption)
    }

    @Test
    fun laterEndTurnsWaitIntoDetour() {
        val earlyEnd = fire.copy(expectedEnd = t("14:50"))
        val old = adviceFor(earlyEnd)
        val new = adviceFor(fire)
        assertEquals(Advice.Wait::class, old::class)
        val u = routeUpdate(RouteCheck(old, listOf(earlyEnd)), RouteCheck(new, listOf(fire)), watched, now)
        assertEquals(UpdateKind.ADVICE_CHANGED, u?.kind)
    }

    @Test
    fun busOnTheAvoidedLineWhileOnTheDetour() {
        // The user took the Yverdon detour: its advice stays Ok, but the fire they avoid gets buses.
        val ok = advise(detour, listOf(fire), now, emptyList())
        val withBus = fire.copy(version = 3, bus = ReplacementBus(BusStatus.SCHEDULED, Fx.PALEZIEUX, eta = t("15:15")))
        val u = routeUpdate(RouteCheck(ok, listOf(fire)), RouteCheck(ok, listOf(withBus)), watched, now)
        assertEquals(UpdateKind.BUS_RUNNING, u?.kind)
        assertEquals(withBus, u?.disruption)
    }

    @Test
    fun endedOrDroppedWatchedDisruption() {
        val ok = Advice.Ok()
        val ended = fire.copy(status = DisruptionStatus.ENDED)
        assertEquals(
            UpdateKind.LINE_REOPENED,
            routeUpdate(RouteCheck(ok, listOf(fire)), RouteCheck(ok, listOf(ended)), watched, now)?.kind,
        )
        assertEquals(
            UpdateKind.LINE_REOPENED,
            routeUpdate(RouteCheck(ok, listOf(fire)), RouteCheck(ok, emptyList()), watched, now)?.kind,
        )
        assertNull(routeUpdate(RouteCheck(ok, listOf(ended)), RouteCheck(ok, emptyList()), watched, now))
    }

    @Test
    fun movedEndOnAWatchedDisruption() {
        val later = fire.copy(expectedEnd = t("16:30"))
        val u = routeUpdate(RouteCheck(null, listOf(fire)), RouteCheck(null, listOf(later)), watched, now)
        assertEquals(UpdateKind.END_CHANGED, u?.kind)
    }

    @Test
    fun unwatchedDisruptionsAreIgnored() {
        val ok = Advice.Ok()
        val later = fire.copy(expectedEnd = t("16:30"))
        assertNull(routeUpdate(RouteCheck(ok, listOf(fire)), RouteCheck(ok, listOf(later)), emptySet(), now))
    }
}
