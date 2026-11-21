package ch.ausweg.server

import ch.ausweg.server.ingest.DisruptionSource
import ch.ausweg.server.ingest.JsonFileSource
import ch.ausweg.server.replay.ModeController
import ch.ausweg.server.replay.Snapshot
import ch.ausweg.server.replay.SystemWallClock
import ch.ausweg.server.replay.WallClock
import ch.ausweg.shared.model.StateResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The server's long-lived parts, built once and handed to the routes. */
class Backend(
    val source: DisruptionSource,
    wall: WallClock = SystemWallClock,
    val sseTick: Duration = 1.seconds,
    val sseHeartbeat: Duration = 5.seconds,
) {
    val mode = ModeController(source, wall)

    /** The clock plus every disruption known at [at] (default: the clock's current time). */
    fun state(at: Instant? = null): StateResponse {
        val clock = mode.clockState().let { if (at != null) it.copy(now = at) else it }
        return StateResponse(clock, Snapshot.at(source.load().updates, clock.now))
    }

    companion object {
        fun fromConfig(config: ServerConfig) = Backend(JsonFileSource(config.eventsFile))
    }
}
