package ch.ausweg.shared.routing

import ch.ausweg.shared.disruption.Fx
import ch.ausweg.shared.disruption.Fx.FRIBOURG
import ch.ausweg.shared.disruption.Fx.LAUSANNE
import ch.ausweg.shared.disruption.Fx.PALEZIEUX
import ch.ausweg.shared.disruption.Fx.PUIDOUX
import ch.ausweg.shared.disruption.Fx.VAUDERENS
import ch.ausweg.shared.disruption.Fx.YVERDON
import ch.ausweg.shared.disruption.Fx.fire
import ch.ausweg.shared.disruption.Fx.viaYverdon
import kotlin.test.Test
import kotlin.test.assertEquals

class RerouteTest {
    private val bern = 8507000

    @Test
    fun viaCandidatesSkipClosedStationsAndEndpoints() {
        val hubs = listOf(PUIDOUX, YVERDON, LAUSANNE, YVERDON, VAUDERENS, bern, PALEZIEUX)
        assertEquals(
            listOf(YVERDON, bern),
            viaCandidates(listOf(fire), hubs, exclude = setOf(LAUSANNE, FRIBOURG), max = 2),
        )
    }

    @Test
    fun viaCandidatesUseFlatAffectedStops() {
        val flat = fire.copy(sections = emptyList(), affectedStops = listOf(YVERDON))
        assertEquals(listOf(bern), viaCandidates(listOf(flat), listOf(YVERDON, bern)))
    }

    @Test
    fun openTripsFirstThenBlockedByArrival() {
        val late = viaYverdon("15:00", "16:40")
        val early = viaYverdon("14:40", "16:10")
        val ranked = rankTrips(listOf(Fx.ic1, late, Fx.viaLucens, early), listOf(fire))
        assertEquals(listOf("yverdon-14:40", "yverdon-15:00", "ic1", "lucens"), ranked.map { it.id })
    }

    @Test
    fun sameJourneyFromTwoSearchesIsKeptOnce() {
        val a = viaYverdon("14:40", "16:10")
        val again = a.copy(id = "other-search-1")
        assertEquals(listOf(a), rankTrips(listOf(a, again), emptyList()))
    }

    @Test
    fun clashingIdsAreMadeUnique() {
        val a = viaYverdon("14:40", "16:10").copy(id = "1")
        val b = viaYverdon("15:40", "17:10").copy(id = "1")
        assertEquals(listOf("1", "1#1"), rankTrips(listOf(a, b), emptyList()).map { it.id })
    }
}
