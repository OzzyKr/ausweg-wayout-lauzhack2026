package ch.ausweg.server.replay

import kotlin.time.Clock
import kotlin.time.Instant

/** Wall-clock source, injectable so tests control time. */
typealias WallClock = () -> Instant

val SystemWallClock: WallClock = { Clock.System.now() }

/**
 * Simulated clock for historical replay: `sim = anchorSim + (wall - anchorWall) * speed` while playing,
 * frozen at `anchorSim` while paused. Every control call re-anchors, so changes never make time jump.
 */
class ReplayClock(
    start: Instant,
    private val wall: WallClock = SystemWallClock,
) {
    private var anchorSim: Instant = start
    private var anchorWall: Instant = wall()

    var speed: Double = 1.0
        private set

    var playing: Boolean = false
        private set

    @Synchronized
    fun now(): Instant = if (playing) anchorSim + (wall() - anchorWall) * speed else anchorSim

    @Synchronized
    fun play() {
        reanchor(now())
        playing = true
    }

    @Synchronized
    fun pause() {
        reanchor(now())
        playing = false
    }

    @Synchronized
    fun seek(to: Instant) = reanchor(to)

    @Synchronized
    fun setSpeed(x: Double) {
        require(x > 0.0 && x <= MAX_SPEED) { "speed must be in (0, $MAX_SPEED]" }
        reanchor(now())
        speed = x
    }

    private fun reanchor(sim: Instant) {
        anchorSim = sim
        anchorWall = wall()
    }

    companion object {
        /** One simulated hour per real second is plenty for a stage demo. */
        const val MAX_SPEED = 3600.0
    }
}
