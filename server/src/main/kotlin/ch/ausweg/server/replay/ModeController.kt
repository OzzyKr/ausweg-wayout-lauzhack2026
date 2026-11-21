package ch.ausweg.server.replay

import ch.ausweg.server.ingest.DisruptionSource
import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.Scenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/**
 * The one global clock every client shares. Live mode uses the wall clock; historical mode replays a
 * [Scenario] on a [ReplayClock]. [revision] increases on every control change so SSE can push it.
 */
class ModeController(
    private val source: DisruptionSource,
    private val wall: WallClock = SystemWallClock,
) {
    private var replay: ReplayClock? = null
    private var scenarioId: String? = null
    private val revisionFlow = MutableStateFlow(0L)

    val revision: StateFlow<Long> = revisionFlow.asStateFlow()

    @Synchronized
    fun now(): Instant = replay?.now() ?: wall()

    @Synchronized
    fun clockState(): ClockState {
        val r = replay ?: return ClockState(Mode.LIVE, wall(), playing = true, speed = 1.0)
        return ClockState(Mode.HISTORICAL, r.now(), r.playing, r.speed, scenarioId)
    }

    /** Switches to historical mode at the scenario's start. Returns null if the id is unknown. */
    @Synchronized
    fun startScenario(
        id: String,
        play: Boolean = true,
        speed: Double? = null,
    ): ClockState? {
        val scenario = source.load().scenarios.find { it.id == id } ?: return null
        replay =
            ReplayClock(scenario.start, wall).also { clock ->
                speed?.let(clock::setSpeed)
                if (play) clock.play()
            }
        scenarioId = scenario.id
        return changed()
    }

    @Synchronized
    fun goLive(): ClockState {
        replay = null
        scenarioId = null
        return changed()
    }

    /** Applies [action] to the replay clock. Returns null in live mode, where the clock can't be controlled. */
    @Synchronized
    fun control(action: (ReplayClock) -> Unit): ClockState? {
        val r = replay ?: return null
        action(r)
        return changed()
    }

    private fun changed(): ClockState {
        revisionFlow.update { it + 1 }
        return clockState()
    }
}
