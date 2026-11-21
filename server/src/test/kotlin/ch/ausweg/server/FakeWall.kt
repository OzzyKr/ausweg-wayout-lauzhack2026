package ch.ausweg.server

import kotlin.time.Instant

/** A wall clock the test moves by hand. */
class FakeWall(
    var now: Instant,
) : () -> Instant {
    override fun invoke(): Instant = now
}
