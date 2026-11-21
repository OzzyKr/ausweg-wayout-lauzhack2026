package ch.ausweg.server.ojp

import ch.ausweg.server.FakeWall
import ch.ausweg.server.OjpXml
import ch.ausweg.server.instant
import ch.ausweg.shared.model.TripSource
import ch.ausweg.shared.ojp.TripQuery
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration

class OjpServiceTest {
    private val wall = FakeWall(instant("2026-11-21T17:00:00Z")) // Saturday
    private val requests = mutableListOf<String>()
    private val nextFriday = OjpXml.response(OjpXml.direct("2026-11-27T15:45:00Z", "2026-11-27T16:30:00Z"))

    private fun service(
        answer: (String) -> String = { nextFriday },
        fixtures: OjpFixtures = OjpFixtures(null),
    ) = OjpService(
        transport = { xml ->
            requests += xml
            answer(xml)
        },
        fixtures = fixtures,
        wall = wall,
        minInterval = Duration.ZERO,
    )

    // Fri 25 Sep 2026, 16:30 CEST.
    private val query = TripQuery("8501120", "8504100", instant("2026-09-25T14:30:00Z"))

    @Test
    fun `historical search asks OJP for the same weekday and moves trips back`() =
        runBlocking<Unit> {
            val found = service().trips(query, historical = true)
            // Next Friday 16:30 CET is 15:30Z.
            assertTrue(requests.single().contains("<DepArrTime>2026-11-27T15:30:00Z</DepArrTime>"))
            assertEquals(TripSource.SHIFTED, found.source)
            // 16:45 local stays 16:45 local: 14:45Z in September.
            assertEquals(instant("2026-09-25T14:45:00Z"), found.trips.single().departure)
            assertTrue(found.situations.isEmpty())
        }

    @Test
    fun `live search passes the time through`() =
        runBlocking<Unit> {
            val live = query.copy(departAt = instant("2026-11-27T15:30:00Z"))
            val found = service().trips(live, historical = false)
            assertEquals(TripSource.LIVE, found.source)
            assertEquals(instant("2026-11-27T15:45:00Z"), found.trips.single().departure)
        }

    @Test
    fun `same search within the cache time hits OJP once`() =
        runBlocking<Unit> {
            val s = service()
            s.trips(query, historical = true)
            s.trips(query, historical = true)
            assertEquals(1, requests.size)
        }

    @Test
    fun `falls back to a recorded fixture moved onto the requested date`() =
        runBlocking<Unit> {
            val dir = Files.createTempDirectory("ojp-fixtures")
            dir.resolve(OjpFixtures.fileName(query)).writeText(nextFriday)
            val found =
                service(
                    answer = { throw OjpUnavailable("down") },
                    fixtures = OjpFixtures(dir),
                ).trips(query, true)
            assertEquals(TripSource.FIXTURE, found.source)
            assertEquals(instant("2026-09-25T14:45:00Z"), found.trips.single().departure)
        }

    @Test
    fun `no key and no fixture is unavailable`() =
        runBlocking<Unit> {
            val s = OjpService(transport = null, wall = wall)
            assertFailsWith<OjpUnavailable> { s.trips(query, historical = true) }
        }

    @Test
    fun `unreadable answer counts as unavailable`() =
        runBlocking<Unit> {
            assertFailsWith<OjpUnavailable> { service(answer = { "not xml" }).trips(query, true) }
        }

    @Test
    fun `recording writes successful answers`() =
        runBlocking<Unit> {
            val dir = Files.createTempDirectory("ojp-record")
            service(fixtures = OjpFixtures(dir, record = true)).trips(query, true)
            assertTrue(dir.resolve("trip_8501120_8504100.xml").exists())
        }

    @Test
    fun `recorded fixtures leave out situations`() {
        val xml =
            "<A><TripResponseContext><Situations><PtSituation>x</PtSituation>\n</Situations>" +
                "</TripResponseContext></A>"
        assertEquals("<A><TripResponseContext></TripResponseContext></A>", OjpFixtures.stripSituations(xml))
    }

    @Test
    fun `fixture names are safe and include vias`() {
        val q = TripQuery("ch:1:sloid:1120", "8504100", instant("2026-09-25T14:30:00Z"), via = listOf("8504200"))
        assertEquals("trip_ch-1-sloid-1120_8504100_via_8504200.xml", OjpFixtures.fileName(q))
    }
}
