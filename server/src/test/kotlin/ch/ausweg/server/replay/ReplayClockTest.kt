package ch.ausweg.server.replay

import ch.ausweg.server.FakeWall
import ch.ausweg.server.instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ReplayClockTest {
    private val start = instant("2026-09-25T14:15:00Z")
    private val wall = FakeWall(instant("2026-11-21T10:00:00Z"))
    private val clock = ReplayClock(start, wall)

    @Test
    fun `starts paused at the scenario start`() {
        wall.now += 10.minutes
        assertEquals(start, clock.now())
    }

    @Test
    fun `advances with wall time times speed while playing`() {
        clock.setSpeed(60.0)
        clock.play()
        wall.now += 30.seconds
        assertEquals(start + 30.minutes, clock.now())
    }

    @Test
    fun `pause freezes time and play resumes without a jump`() {
        clock.play()
        wall.now += 1.minutes
        clock.pause()
        wall.now += 10.minutes
        assertEquals(start + 1.minutes, clock.now())
        clock.play()
        wall.now += 1.minutes
        assertEquals(start + 2.minutes, clock.now())
    }

    @Test
    fun `changing speed keeps the current time`() {
        clock.play()
        wall.now += 1.minutes
        clock.setSpeed(10.0)
        assertEquals(start + 1.minutes, clock.now())
        wall.now += 1.minutes
        assertEquals(start + 11.minutes, clock.now())
    }

    @Test
    fun `seek jumps and keeps playing`() {
        clock.play()
        val target = instant("2026-09-25T15:46:00Z")
        clock.seek(target)
        wall.now += 5.seconds
        assertEquals(target + 5.seconds, clock.now())
    }

    @Test
    fun `rejects speeds out of range`() {
        assertFailsWith<IllegalArgumentException> { clock.setSpeed(0.0) }
        assertFailsWith<IllegalArgumentException> { clock.setSpeed(ReplayClock.MAX_SPEED + 1) }
    }
}
