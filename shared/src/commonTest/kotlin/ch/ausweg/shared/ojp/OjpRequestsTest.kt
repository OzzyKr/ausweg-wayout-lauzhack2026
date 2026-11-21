package ch.ausweg.shared.ojp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class OjpRequestsTest {
    private val now = Instant.parse("2026-11-21T10:00:00Z")
    private val departAt = Instant.parse("2026-09-25T15:20:00Z")

    @Test
    fun tripRequestIsValidOjp20() {
        val xml = OjpRequests.trip(TripQuery("8501120", "ch:1:sloid:4100", departAt), now, "ausweg_test")
        assertTrue(xml.startsWith("<?xml"), "XML declaration must come first")

        val root = XmlNode.parse(xml)
        assertEquals("OJP", root.name)
        assertEquals("2.0", root.attributes["version"])
        val request = root.at("OJPRequest", "ServiceRequest")!!
        assertEquals("ausweg_test", request.textAt("RequestorRef"))
        val trip = request.child("OJPTripRequest")!!
        assertEquals("8501120", trip.textAt("Origin", "PlaceRef", "StopPointRef"))
        assertEquals(departAt.toString(), trip.textAt("Origin", "DepArrTime"))
        assertEquals("ch:1:sloid:4100", trip.textAt("Destination", "PlaceRef", "StopPointRef"))
        assertEquals("true", trip.textAt("Params", "IncludeIntermediateStops"))
        assertEquals("5", trip.textAt("Params", "NumberOfResults"))
        assertTrue(trip.children("Via").isEmpty())
    }

    @Test
    fun tripRequestAddsViaPoints() {
        val query = TripQuery("8501120", "8504100", departAt, via = listOf("8504014", "8504200"))
        val xml = OjpRequests.trip(query, now, "ausweg_test")
        val vias =
            XmlNode
                .parse(xml)
                .descendants("ViaPoint")
                .map { it.textAt("StopPointRef") }
                .toList()
        assertEquals(listOf("8504014", "8504200"), vias)
    }

    @Test
    fun locationRequestEscapesUserInput() {
        val query = """Gare <Nord> & "Sud" 'x'"""
        val xml = OjpRequests.location(query, now, "ausweg_test")
        assertFalse(xml.contains("<Nord>"))

        val request = XmlNode.parse(xml).descendants("OJPLocationInformationRequest").single()
        assertEquals(query, request.textAt("InitialInput", "Name"))
        assertEquals("stop", request.textAt("Restrictions", "Type"))
        assertEquals("10", request.textAt("Restrictions", "NumberOfResults"))
    }
}
