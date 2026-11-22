package ch.ausweg.shared.state

import ch.ausweg.shared.model.VoteValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VotingTest {
    private val api = FakeApi()

    @Test
    fun theSameVoteAgainTakesItBack() =
        runTest {
            val voting = Voting(api, MemorySettings())
            assertNotNull(voting.tip("t1", VoteValue.UP))
            assertEquals(VoteValue.UP, voting.mine.value["t1"])

            assertNotNull(voting.tip("t1", VoteValue.UP))
            assertNull(voting.mine.value["t1"])

            voting.tip("t1", VoteValue.DOWN)
            voting.tip("t1", VoteValue.UP)
            assertEquals(VoteValue.UP, voting.mine.value["t1"], "another value replaces, it doesn't take back")
            assertEquals(listOf("voteTip t1 UP", "voteTip t1 null", "voteTip t1 DOWN", "voteTip t1 UP"), api.calls)
        }

    @Test
    fun votesAreKeptAcrossRestarts() =
        runTest {
            val settings = MemorySettings()
            Voting(api, settings).tip("t1", VoteValue.STILL_VALID)

            val again = Voting(api, settings)
            assertEquals(mapOf("t1" to VoteValue.STILL_VALID), again.mine.value)
            again.tip("t1", VoteValue.STILL_VALID)
            assertTrue(Voting(api, settings).mine.value.isEmpty())
        }

    @Test
    fun aFailedVoteChangesNothing() =
        runTest {
            val voting = Voting(api, MemorySettings())
            api.voteError = IllegalStateException("offline")
            assertNull(voting.tip("t1", VoteValue.UP))
            assertTrue(voting.mine.value.isEmpty())
        }

    @Test
    fun brokenSavedVotesAreIgnored() {
        val settings = MemorySettings(mapOf(SettingKeys.MY_VOTES to "not json"))
        assertTrue(Voting(api, settings).mine.value.isEmpty())
    }
}
