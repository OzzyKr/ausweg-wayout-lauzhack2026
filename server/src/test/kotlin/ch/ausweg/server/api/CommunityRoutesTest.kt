package ch.ausweg.server.api

import ch.ausweg.server.AuswegJson
import ch.ausweg.server.Backend
import ch.ausweg.server.BackendServices
import ch.ausweg.server.FakeWall
import ch.ausweg.server.ingest.ScenarioDirSource
import ch.ausweg.server.instant
import ch.ausweg.server.module
import ch.ausweg.server.tips.CommunityStore
import ch.ausweg.shared.model.CommunityMatch
import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.RankedRoute
import ch.ausweg.shared.model.RankedTip
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.TipKind
import ch.ausweg.shared.model.Votes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommunityRoutesTest {
    private val wall = FakeWall(instant("2026-11-21T17:00:00Z"))

    /** Seeded app; [replayAt] puts the clock in the Puidoux fire. */
    private fun withApp(
        replayAt: String? = null,
        block: suspend ApplicationTestBuilder.(Backend) -> Unit,
    ) = testApplication {
        val store = CommunityStore.inMemory().also { it.seedIfEmpty(Path.of("../data/seed/community.json")) }
        val backend =
            Backend(
                ScenarioDirSource(Path.of("../data/scenarios")),
                wall,
                services = BackendServices(community = store, staffToken = "staff-secret"),
            )
        if (replayAt != null) {
            backend.mode.startScenario("puidoux-vauderens_2026-09-25", play = false)
            backend.mode.control { it.seek(instant(replayAt)) }
        }
        application { module(backend) }
        block(backend)
    }

    private suspend fun ApplicationTestBuilder.postJson(
        path: String,
        body: String,
    ): HttpResponse =
        client.post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend inline fun <reified T> HttpResponse.decode(): T = AuswegJson.decodeFromString(bodyAsText())

    @Test
    fun `during the fire, tips from the similar 12 Sep disruption come first`() =
        withApp(replayAt = "2026-09-25T14:30:00Z") {
            val tips = client.get("/tips?stations=8501120,8504100").decode<List<RankedTip>>()
            assertEquals(
                listOf("seed-tip-yverdon", "seed-tip-bern", "seed-tip-lausanne-1-7", "seed-tip-fribourg-2-1"),
                tips.map { it.tip.id },
            )
            assertEquals(CommunityMatch.SIMILAR_DISRUPTION, tips.first().match)
            // Renens tip (other stretch) and the route note are not in the list.
        }

    @Test
    fun `live with no disruption only transfer tips show`() =
        withApp {
            val tips = client.get("/tips").decode<List<RankedTip>>()
            assertTrue(tips.all { it.tip.kind == TipKind.TRANSFER })
        }

    @Test
    fun `routes come with their notes`() =
        withApp(replayAt = "2026-09-25T14:30:00Z") {
            val routes = client.get("/routes?from=8501120&to=8504100").decode<List<RankedRoute>>()
            assertEquals("seed-route-yverdon", routes.single().route.id)
            assertEquals(listOf("seed-note-yverdon-change"), routes.single().notes.map { it.id })
            assertTrue(client.get("/routes?from=8501120&to=8501008").decode<List<RankedRoute>>().isEmpty())
        }

    @Test
    fun `a tip written during the fire takes its sections and the replay time`() =
        withApp(replayAt = "2026-09-25T14:30:00Z") {
            val res =
                postJson(
                    "/tips",
                    """{"kind":"DISRUPTION","text":"  Bus from Palézieux works  ","disruptionId":"2195005195853"}""",
                )
            assertEquals(HttpStatusCode.Created, res.status)
            val tip = res.decode<Tip>()
            assertEquals("Bus from Palézieux works", tip.text)
            assertEquals(instant("2026-09-25T14:30:00Z"), tip.createdAt)
            assertEquals(2, tip.sections.size)
            val tips = client.get("/tips").decode<List<RankedTip>>()
            assertEquals(tip.id to CommunityMatch.SAME_DISRUPTION, tips.first().let { it.tip.id to it.match })
        }

    @Test
    fun `bad tips are refused`() =
        withApp {
            assertEquals(HttpStatusCode.BadRequest, postJson("/tips", """{"kind":"DISRUPTION","text":"   "}""").status)
            assertEquals(
                HttpStatusCode.BadRequest,
                postJson("/tips", """{"kind":"TRANSFER","text":"no station"}""").status,
            )
        }

    @Test
    fun `votes count once per voter`() =
        withApp {
            val vote = """{"voterId":"device-1","value":"UP"}"""
            postJson("/tips/seed-tip-fribourg-2-1/vote", vote)
            val tip = postJson("/tips/seed-tip-fribourg-2-1/vote", vote).decode<Tip>()
            assertEquals(Votes(up = 4), tip.votes)
            val back = postJson("/tips/seed-tip-fribourg-2-1/vote", """{"voterId":"device-1"}""").decode<Tip>()
            assertEquals(Votes(up = 3), back.votes, "no value takes the vote back")
            assertEquals(HttpStatusCode.NotFound, postJson("/tips/nope/vote", vote).status)
            assertEquals(
                HttpStatusCode.BadRequest,
                postJson("/tips/seed-tip-fribourg-2-1/vote", """{"voterId":"a b","value":"UP"}""").status,
            )
        }

    @Test
    fun `sharing a route stores its notes on the changes`() =
        withApp(replayAt = "2026-09-25T14:30:00Z") {
            val lausanne = """{"ref":"8501120","name":"Lausanne","didok":8501120}"""
            val yverdon = """{"ref":"8504200","name":"Yverdon-les-Bains","didok":8504200}"""
            val fribourg = """{"ref":"8504100","name":"Fribourg/Freiburg","didok":8504100}"""
            val body =
                """{"disruptionId":"2195005195853","legs":[""" +
                    """{"from":$lausanne,"to":$yverdon,"line":"IC5"},""" +
                    """{"from":$yverdon,"to":$fribourg,"line":"S30"}],""" +
                    """"notes":[{"legIndex":0,"text":"Same platform"}]}"""
            val route = postJson("/routes", body).decode<CommunityRoute>()
            assertEquals(8501120, route.from.didok)
            val listed = client.get("/routes?from=8501120&to=8504100").decode<List<RankedRoute>>()
            val mine = listed.single { it.route.id == route.id }
            assertEquals(CommunityMatch.SAME_DISRUPTION, mine.match)
            assertEquals(8504200, mine.notes.single().station)

            val badNote = body.replace("\"legIndex\":0", "\"legIndex\":1")
            assertEquals(HttpStatusCode.BadRequest, postJson("/routes", badNote).status)
        }

    @Test
    fun `only staff can endorse`() =
        withApp { backend ->
            assertEquals(HttpStatusCode.Forbidden, client.post("/tips/seed-tip-bern/endorse").status)
            val ok = client.post("/tips/seed-tip-bern/endorse") { header(STAFF_TOKEN_HEADER, "staff-secret") }
            assertEquals(HttpStatusCode.NoContent, ok.status)
            val missing = client.post("/routes/nope/endorse") { header(STAFF_TOKEN_HEADER, "staff-secret") }
            assertEquals(HttpStatusCode.NotFound, missing.status)
            assertTrue(backend.community.revision.value > 0)
        }
}
