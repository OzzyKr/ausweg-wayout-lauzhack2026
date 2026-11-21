package ch.ausweg.shared.disruption

import ch.ausweg.shared.disruption.Fx.PALEZIEUX
import ch.ausweg.shared.disruption.Fx.PUIDOUX
import ch.ausweg.shared.disruption.Fx.VAUDERENS
import ch.ausweg.shared.disruption.Fx.fire
import ch.ausweg.shared.disruption.Fx.ic1
import ch.ausweg.shared.disruption.Fx.leg
import ch.ausweg.shared.disruption.Fx.t
import ch.ausweg.shared.disruption.Fx.trip
import ch.ausweg.shared.disruption.Fx.viaLucens
import ch.ausweg.shared.disruption.Fx.viaYverdon
import ch.ausweg.shared.model.Advice
import ch.ausweg.shared.model.AdviceReason
import ch.ausweg.shared.model.BusStatus
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.EndType
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.ReplacementBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Fire ends 16:00 «environ»; IC1 takes 45 min, so waiting arrives at 16:45. Clock is 14:30. */
class AdvisorTest {
    private val now = t("14:30")
    private val openEnded = fire.copy(expectedEnd = null, expectedEndType = null)

    @Test
    fun unaffectedTripIsOk() {
        val info = fire.copy(hints = listOf(DisruptionHints.NO_ALTERNATIVE))
        assertEquals(Advice.Ok(warnings = listOf(info)), advise(ic1, listOf(info), now, emptyList()))
    }

    @Test
    fun knownEndAndNoAlternativeMeansWait() {
        val advice = assertIs<Advice.Wait>(advise(ic1, listOf(fire), now, listOf(viaLucens)))
        assertEquals(t("16:00"), advice.until)
        assertEquals(EndType.APPROX, advice.endType)
        assertEquals(AdviceReason.WAIT_IS_FASTER, advice.reason)
        assertEquals(listOf(fire), advice.impact.blocking)
    }

    @Test
    fun muchFasterDetourWins() {
        val slow = viaYverdon("14:50", "16:20")
        val fast = viaYverdon("14:40", "16:10")
        val advice = assertIs<Advice.Detour>(advise(ic1, listOf(fire), now, listOf(slow, viaLucens, fast)))
        assertEquals(AdviceReason.DETOUR_IS_FASTER, advice.reason)
        assertEquals(listOf(fast, slow), advice.alternatives)
    }

    @Test
    fun alternativesThatAlreadyLeftAreIgnored() {
        // At 15:00 the 14:40 detour has left; the 15:10 one is the only usable alternative.
        val gone = viaYverdon("14:40", "16:10")
        val later = viaYverdon("15:10", "16:20")
        val advice = assertIs<Advice.Detour>(advise(ic1, listOf(fire), t("15:00"), listOf(gone, later)))
        assertEquals(listOf(later), advice.alternatives)
        // With only the departed one, nothing gets round: wait.
        assertIs<Advice.Wait>(advise(ic1, listOf(fire), t("15:00"), listOf(gone)))
    }

    @Test
    fun waitWinsWithinTheMargin() {
        // Wait arrives 16:45; detour at 16:40 is only 5 min earlier: waiting is less hassle.
        val nearlySame = viaYverdon("15:10", "16:40")
        assertIs<Advice.Wait>(advise(ic1, listOf(fire), now, listOf(nearlySame)))
        val justFaster = viaYverdon("15:09", "16:39")
        assertIs<Advice.Detour>(advise(ic1, listOf(fire), now, listOf(justFaster)))
    }

    @Test
    fun waitCountsFromNowWhenTheEndHasPassed() {
        // Still active at 15:50 though «until 15:00»: waiting arrives 15:50 + 45 min = 16:35, not 15:45.
        val overdue = listOf(fire.copy(expectedEnd = t("15:00")))
        val detour = viaYverdon("15:55", "16:20")
        assertIs<Advice.Detour>(advise(ic1, overdue, t("15:50"), listOf(detour)))
        assertIs<Advice.Wait>(advise(ic1, overdue, t("14:30"), listOf(detour)))
    }

    @Test
    fun latestEndOfSeveralBlockingDisruptionsCounts() {
        val second = fire.copy(id = "other", expectedEnd = t("17:00"), expectedEndType = EndType.AT_LEAST)
        val advice = assertIs<Advice.Wait>(advise(ic1, listOf(fire, second), now, emptyList()))
        assertEquals(t("17:00"), advice.until)
        assertEquals(EndType.AT_LEAST, advice.endType)
    }

    @Test
    fun unknownEndPrefersDetour() {
        val advice = assertIs<Advice.Detour>(advise(ic1, listOf(openEnded), now, listOf(viaYverdon("15:30", "17:30"))))
        assertEquals(AdviceReason.OPEN_ENDED, advice.reason)
    }

    @Test
    fun replacementBusTripCountsAsAlternative() {
        val busHint = openEnded.copy(hints = listOf(DisruptionHints.REPLACEMENT_BUS))
        val ev = trip("ev", "14:50", "16:00", leg(PUIDOUX, PALEZIEUX, VAUDERENS, dep = "14:50", mode = LegMode.BUS))
        val advice = assertIs<Advice.Detour>(advise(ic1, listOf(busHint), now, listOf(ev)))
        assertEquals(listOf(ev), advice.alternatives)
    }

    @Test
    fun unknownEndNoAlternativeButBusMeansTakeBus() {
        val bus = ReplacementBus(BusStatus.SCHEDULED, from = PALEZIEUX, eta = t("15:15"))
        val advice = assertIs<Advice.TakeBus>(advise(ic1, listOf(openEnded.copy(bus = bus)), now, listOf(viaLucens)))
        assertEquals(bus, advice.bus)
        assertEquals(AdviceReason.BUS_AVAILABLE, advice.reason)
    }

    @Test
    fun nothingKnownMeansWaitWithoutEnd() {
        val advice = assertIs<Advice.Wait>(advise(ic1, listOf(openEnded), now, emptyList()))
        assertEquals(null, advice.until)
        assertEquals(AdviceReason.NO_ALTERNATIVE, advice.reason)
    }

    @Test
    fun cancelledLegWithoutDisruptionHasNoKnownEnd() {
        val cancelled = ic1.copy(legs = ic1.legs.map { it.copy(cancelled = true) })
        val advice = assertIs<Advice.Wait>(advise(cancelled, emptyList(), now, emptyList()))
        assertEquals(AdviceReason.NO_ALTERNATIVE, advice.reason)
        assertEquals(listOf(0), advice.impact.cancelledLegs)
    }
}
