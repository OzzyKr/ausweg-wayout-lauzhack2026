package ch.ausweg.server.ingest

import ch.ausweg.server.AuswegJson
import ch.ausweg.server.Backend
import ch.ausweg.server.FakeWall
import ch.ausweg.server.instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The `raw` text of scenario events (internal ticker originals) must never leave the server. */
class PrivacyTest {
    private val dir = Path.of("../data/scenarios")

    @Test
    fun `no raw text in any state the app can receive`() {
        val backend = Backend(ScenarioDirSource(dir), FakeWall(instant("2026-11-21T17:00:00Z")))
        val files = Files.list(dir).use { s -> s.filter { it.name.endsWith(SCENARIO_SUFFIX) }.toList() }
        assertTrue(files.isNotEmpty())
        files.forEach { file ->
            val events = AuswegJson.parseToJsonElement(file.readText()).jsonObject["events"]!!.jsonArray
            // Skips short VIA column names (`zeitpunkt_erstpublikation`), which could match by accident.
            val raws = events.mapNotNull { (it as JsonObject)["raw"]?.jsonPrimitive?.content }.filter { it.length > 40 }
            val times = events.map { instant(it.jsonObject["t"]!!.jsonPrimitive.content) }
            times.forEach { t ->
                val json = AuswegJson.encodeToString(backend.state(t))
                raws.forEach { raw -> assertFalse(json.contains(raw), "raw text leaked at $t in ${file.name}") }
            }
        }
    }
}
