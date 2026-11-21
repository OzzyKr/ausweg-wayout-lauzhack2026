package ch.ausweg.server.tips

import ch.ausweg.server.instant
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.TipKind
import ch.ausweg.shared.model.VoteValue
import ch.ausweg.shared.model.Votes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityStoreTest {
    private val dir = Files.createTempDirectory("community")
    private val db = dir.resolve("sub/ausweg.db")
    private val seed = Path.of("../data/seed/community.json")

    private fun tip(id: String) =
        Tip(id, TipKind.TRANSFER, "text $id", instant("2026-09-25T14:30:00Z"), station = 8501120)

    @Test
    fun `one vote per voter, and a new vote replaces the old one`() {
        val store = CommunityStore.inMemory()
        store.addTip(tip("t1"))
        assertTrue(store.vote("t1", "device-a", VoteValue.UP))
        assertTrue(store.vote("t1", "device-a", VoteValue.UP))
        assertTrue(store.vote("t1", "device-b", VoteValue.UP))
        assertEquals(Votes(up = 2), store.tips().single().votes)
        store.vote("t1", "device-a", VoteValue.DOWN)
        assertEquals(Votes(up = 1, down = 1), store.tips().single().votes)
    }

    @Test
    fun `voting on or endorsing an unknown id fails`() {
        val store = CommunityStore.inMemory()
        assertFalse(store.vote("nope", "device-a", VoteValue.UP))
        assertFalse(store.endorse("nope"))
    }

    @Test
    fun `endorsement is stored`() {
        val store = CommunityStore.inMemory()
        store.addTip(tip("t1"))
        assertTrue(store.endorse("t1"))
        assertTrue(store.tips().single().staffEndorsed)
    }

    @Test
    fun `data survives reopening the file`() {
        CommunityStore.open(db).apply {
            addTip(tip("t1"))
            vote("t1", "device-a", VoteValue.STILL_VALID)
        }
        val reopened = CommunityStore.open(db).tips().single()
        assertEquals("t1", reopened.id)
        assertEquals(Votes(stillValid = 1), reopened.votes)
    }

    @Test
    fun `the seed loads once, with its votes and endorsements`() {
        val store = CommunityStore.open(db)
        store.seedIfEmpty(seed)
        val tips = store.tips()
        assertEquals(6, tips.size)
        assertEquals(1, store.routes().size)
        val yverdon = tips.single { it.id == "seed-tip-yverdon" }
        assertEquals(Votes(up = 14, down = 1, stillValid = 3), yverdon.votes)
        assertTrue(yverdon.staffEndorsed)

        store.seedIfEmpty(seed)
        assertEquals(6, store.tips().size)
    }
}
