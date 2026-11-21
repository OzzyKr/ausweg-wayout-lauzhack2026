package ch.ausweg.server.replay

import ch.ausweg.server.instant
import ch.ausweg.server.update
import ch.ausweg.shared.model.DisruptionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotTest {
    private val updates =
        listOf(
            update("A", 1, "2026-09-01T08:00:00Z"),
            update("A", 2, "2026-09-01T08:30:00Z"),
            update("A", 3, "2026-09-01T09:00:00Z", DisruptionStatus.ENDED),
            update("B", 1, "2026-09-01T08:15:00Z"),
            update("B", 2, "2026-09-01T08:20:00Z", deleted = true),
        )

    private fun versionsAt(t: String) = Snapshot.at(updates, instant(t)).map { it.id to it.version }

    @Test
    fun `nothing is known before the first publication`() {
        assertTrue(versionsAt("2026-09-01T07:59:59Z").isEmpty())
    }

    @Test
    fun `a version is visible from the exact moment it is published`() {
        assertEquals(listOf("A" to 1), versionsAt("2026-09-01T08:00:00Z"))
    }

    @Test
    fun `shows the latest version of each disruption, oldest first`() {
        assertEquals(listOf("A" to 1, "B" to 1), versionsAt("2026-09-01T08:16:00Z"))
        assertEquals(listOf("A" to 2), versionsAt("2026-09-01T08:45:00Z"))
    }

    @Test
    fun `deleted disruptions disappear`() {
        assertEquals(listOf("A" to 1), versionsAt("2026-09-01T08:20:00Z"))
    }

    @Test
    fun `ended disruptions stay visible for the retention period only`() {
        assertEquals(listOf("A" to 3), versionsAt("2026-09-01T10:00:00Z"))
        assertTrue(versionsAt("2026-09-01T10:00:01Z").isEmpty())
    }

    @Test
    fun `active disruptions without updates go stale`() {
        val open = listOf(update("C", 1, "2024-03-01T10:00:00Z"))
        assertEquals(1, Snapshot.at(open, instant("2024-03-02T10:00:00Z")).size)
        assertTrue(Snapshot.at(open, instant("2024-03-02T10:00:01Z")).isEmpty())
    }

    @Test
    fun `same timestamp is broken by version`() {
        val tie = listOf(update("D", 2, "2026-09-01T08:00:00Z"), update("D", 1, "2026-09-01T08:00:00Z"))
        assertEquals(2, Snapshot.at(tie, instant("2026-09-01T08:00:00Z")).single().version)
    }
}
