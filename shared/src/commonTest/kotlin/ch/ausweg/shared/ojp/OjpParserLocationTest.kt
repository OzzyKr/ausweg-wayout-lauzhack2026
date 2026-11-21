package ch.ausweg.shared.ojp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OjpParserLocationTest {
    @Test
    fun readsStopsInOrderAndSkipsNonStops() {
        val response = OjpParser.locations(OjpFixtures.LOCATIONS)
        assertNull(response.error)
        assertEquals(listOf("Lausanne", "Lausanne-Flon"), response.locations.map { it.stop.name })

        val lausanne = response.locations[0]
        assertEquals("ch:1:sloid:1120", lausanne.stop.ref)
        assertEquals(8501120, lausanne.stop.didok)
        assertEquals(46.51679, lausanne.latitude)
        assertEquals(6.62909, lausanne.longitude)

        val flon = response.locations[1]
        assertEquals(8501210, flon.stop.didok)
        assertNull(flon.latitude)
    }

    @Test
    fun noResultsIsAnErrorCode() {
        val response = OjpParser.locations(OjpFixtures.LOCATIONS_NONE)
        assertTrue(response.locations.isEmpty())
        assertEquals("LOCATION_NORESULTS", response.error)
    }
}
