package ch.ausweg.shared.tips

import ch.ausweg.shared.disruption.Fx
import ch.ausweg.shared.disruption.Fx.t
import ch.ausweg.shared.model.LegMode
import ch.ausweg.shared.model.StopRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteEditsTest {
    private fun stop(
        didok: Int,
        name: String,
    ) = StopRef("ch:1:sloid:${didok - 8_500_000}", name, didok)

    private val lausanne = stop(Fx.LAUSANNE, "Lausanne")
    private val yverdon = stop(Fx.YVERDON, "Yverdon-les-Bains")
    private val renens = stop(8501117, "Renens VD")
    private val morges = stop(8501037, "Morges")

    private val direct =
        StripLeg(
            "IR15",
            StripKind.TRAIN,
            lausanne,
            renens,
            departure = t("14:40"),
            arrival = t("14:46"),
            departurePlatform = "5",
            arrivalPlatform = "2",
        )

    private fun List<StripLeg>.names() = listOf(first().from.name) + map { it.to.name }

    @Test
    fun aNewChangeSplitsTheRide() {
        val legs = listOf(direct).withChangeAt(0, yverdon)
        assertEquals(listOf("Lausanne", "Yverdon-les-Bains", "Renens VD"), legs.names())
        val (first, second) = legs
        assertEquals("IR15", first.label)
        assertEquals(t("14:40"), first.departure)
        assertEquals("5", first.departurePlatform)
        assertNull(first.arrival, "the arrival was at Renens")
        assertNull(first.arrivalPlatform)
        assertNull(second.label)
        assertNull(second.arrival)
        assertEquals(StripKind.TRAIN, second.kind)
        assertEquals(listOf(direct), listOf(direct).withChangeAt(3, yverdon), "no such ride")
    }

    @Test
    fun replacingAStopForgetsTimesThereOnly() {
        val legs = listOf(direct).withChangeAt(0, yverdon).withStop(1, morges)
        assertEquals(listOf("Lausanne", "Morges", "Renens VD"), legs.names())
        assertEquals(t("14:40"), legs[0].departure)

        val end = listOf(direct).withStop(1, yverdon).single()
        assertEquals(t("14:40"), end.departure)
        assertNull(end.arrival)
        val start = listOf(direct).withStop(0, morges).single()
        assertNull(start.departure)
        assertNull(start.departurePlatform)
        assertEquals("2", start.arrivalPlatform)
    }

    @Test
    fun leavingOutAChangeJoinsTheRides() {
        val split = listOf(direct).withChangeAt(0, yverdon)
        val joined = split.withoutChange(1).single()
        assertEquals(lausanne, joined.from)
        assertEquals(renens, joined.to)
        assertEquals("IR15", joined.label)
        assertEquals(split, split.withoutChange(0), "the start stays")
        assertEquals(split, split.withoutChange(2), "the end stays")
        val unnamed = split.map { it.copy(label = if (it.from == lausanne) null else "R3") }
        assertEquals("R3", unnamed.withoutChange(1).single().label)
    }

    @Test
    fun trainOrBus() {
        val bus = listOf(direct).withBus(0, true).single()
        assertTrue(bus.isBus)
        assertEquals(LegMode.BUS, bus.mode)
        val train = listOf(bus).withBus(0, false).single()
        assertFalse(train.isBus)
        assertEquals(LegMode.RAIL, train.mode)
        assertTrue(direct.copy(kind = StripKind.REPLACEMENT_BUS).isBus)
    }

    @Test
    fun sameStopByNumberElseByRef() {
        assertTrue(lausanne.isSameStop(lausanne.copy(ref = "8501120", name = "Lausanne gare")))
        assertFalse(lausanne.isSameStop(renens))
        assertTrue(StopRef("x", "A").isSameStop(StopRef("x", "B")))
        assertFalse(StopRef("x", "A").isSameStop(StopRef("y", "A")))
    }
}
