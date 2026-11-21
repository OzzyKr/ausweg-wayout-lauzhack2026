package ch.ausweg.server.ingest

import ch.ausweg.server.instant
import ch.ausweg.server.resourcePath
import ch.ausweg.shared.model.DisruptionStatus
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class JsonFileSourceTest {
    @Test
    fun `parses the provisional format and maps the lifecycle`() {
        val data = JsonFileSource(resourcePath("events-fixture.json")).load()
        assertEquals(5, data.updates.size)
        val a3 = data.updates.single { it.disruption.id == "A" && it.disruption.version == 3 }
        assertEquals(DisruptionStatus.ENDED, a3.disruption.status)
        assertTrue(data.updates.single { it.disruption.id == "B" && it.disruption.version == 2 }.deleted)
        assertEquals(
            listOf(8501120),
            data.updates
                .first()
                .disruption.affectedStops,
        )
    }

    @Test
    fun `derives one scenario per disruption when none are curated`() {
        val scenarios = JsonFileSource(resourcePath("events-fixture.json")).load().scenarios
        assertEquals(listOf("A", "B"), scenarios.map { it.id })
        val a = scenarios.first()
        assertEquals("Restriction A", a.title)
        assertEquals(instant("2026-09-01T08:00:00Z") - SCENARIO_LEAD_IN, a.start)
        assertEquals(instant("2026-09-01T09:00:00Z") + SCENARIO_TAIL, a.end)
        assertEquals("Unterbruch B", scenarios[1].title)
    }

    @Test
    fun `the seed file loads with its curated scenario`() {
        val data =
            JsonFileSource(
                java.nio.file.Path
                    .of("../data/seed/events.json"),
            ).load()
        assertTrue(data.updates.isNotEmpty())
        assertEquals("puidoux-fire-2026-09-25", data.scenarios.single().id)
    }

    @Test
    fun `reloads when the file changes and keeps old data when it breaks`() {
        val file = createTempFile(suffix = ".json")
        try {
            file.writeText("""{"updates":[]}""")
            val source = JsonFileSource(file)
            assertTrue(source.load().updates.isEmpty())

            file.writeText("""{"updates":[{"disruptionId":"X","publishedAt":"2026-09-01T08:00:00Z"}]}""")
            touch(file, 1)
            assertEquals(1, source.load().updates.size)

            file.writeText("not json")
            touch(file, 2)
            assertEquals(1, source.load().updates.size)
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun `a missing file serves no data`() {
        val data =
            JsonFileSource(
                java.nio.file.Path
                    .of("does/not/exist.json"),
            ).load()
        assertTrue(data.updates.isEmpty() && data.scenarios.isEmpty())
    }

    /** Sets a distinct mtime so the change is seen even on filesystems with coarse timestamps. */
    private fun touch(
        file: java.nio.file.Path,
        step: Int,
    ) {
        val base = instant("2026-01-01T00:00:00Z") + (step * 10).minutes
        Files.setLastModifiedTime(file, FileTime.fromMillis(base.toEpochMilliseconds()))
    }
}
