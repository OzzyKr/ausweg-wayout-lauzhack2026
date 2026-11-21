package ch.ausweg.shared.state

import ch.ausweg.shared.api.AuswegApi
import ch.ausweg.shared.api.LinkStatus
import ch.ausweg.shared.api.ServerEvent
import ch.ausweg.shared.api.attempt
import ch.ausweg.shared.api.liveEvents
import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.Mode
import ch.ausweg.shared.model.Scenario
import ch.ausweg.shared.model.TimelineEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Replay speeds the speed chip cycles through (simulated seconds per real second). */
val REPLAY_SPEEDS: List<Double> = listOf(1.0, 10.0, 60.0, 300.0)

/** The speed after [current] in [REPLAY_SPEEDS], wrapping around. */
fun nextSpeed(current: Double): Double = REPLAY_SPEEDS.firstOrNull { it > current } ?: REPLAY_SPEEDS.first()

/**
 * The backend clock's time [elapsed] real time after [clock] was received: it runs on at `speed` while playing.
 * Lets the replay bar and trip progress move smoothly between the server's ~5 s heartbeats.
 */
fun displayNow(
    clock: ClockState,
    elapsed: Duration,
): Instant = if (clock.playing) clock.now + elapsed * clock.speed else clock.now

/**
 * The disruptions to judge trips by: the backend's, or, in live mode only, [planDisruptions] (those of a `/plan`
 * answer) while the backend pushes none. Live OJP disruptions exist only in `/plan` answers. In the replay an empty
 * list is real (before the disruption, or an hour after it ended) and must not bring a stale copy back.
 */
fun AppSession.disruptionsFor(planDisruptions: List<Disruption>): List<Disruption> =
    currentDisruptions(disruptions.value, planDisruptions, isLive)

/** [pushed], or [planDisruptions] when [live] and the backend pushed none. See [disruptionsFor]. */
internal fun currentDisruptions(
    pushed: List<Disruption>,
    planDisruptions: List<Disruption>,
    live: Boolean,
): List<Disruption> = if (live) pushed.ifEmpty { planDisruptions } else pushed

/** Where [at] is in [scenario], 0..1 (replay slider). */
fun replayFraction(
    scenario: Scenario,
    at: Instant,
): Float {
    val total = scenario.end - scenario.start
    if (total <= Duration.ZERO) return 0f
    return ((at - scenario.start) / total).toFloat().coerceIn(0f, 1f)
}

/** The instant at [fraction] (0..1) of [scenario] (replay slider seek), to the second. */
fun replayInstant(
    scenario: Scenario,
    fraction: Float,
): Instant {
    val at = scenario.start + (scenario.end - scenario.start) * fraction.coerceIn(0f, 1f).toDouble()
    return Instant.fromEpochSeconds((at + 500.milliseconds).epochSeconds)
}

/**
 * Everything the backend pushes: clock, disruptions, timeline, scenarios, community revision, link status. One per
 * app. Call [start] once.
 */
class AppSession(
    private val api: AuswegApi,
    private val scope: CoroutineScope,
    private val realClock: Clock = Clock.System,
    private val tick: Duration = 500.milliseconds,
) {
    private val _clock = MutableStateFlow<ClockState?>(null)
    private var receivedAt: Instant = realClock.now()
    private val _now = MutableStateFlow<Instant?>(null)
    private val _disruptions = MutableStateFlow<List<Disruption>>(emptyList())
    private val _timeline = MutableStateFlow<List<TimelineEntry>>(emptyList())
    private val _scenarios = MutableStateFlow<List<Scenario>>(emptyList())
    private val _community = MutableStateFlow(0L)
    private val _link = MutableStateFlow(LinkStatus.OFFLINE)
    private val _controlFailed = MutableStateFlow(false)

    val clock: StateFlow<ClockState?> = _clock.asStateFlow()

    /** The backend clock as it ticks on the screen (updated every [tick] while playing). */
    val now: StateFlow<Instant?> = _now.asStateFlow()
    val disruptions: StateFlow<List<Disruption>> = _disruptions.asStateFlow()
    val timeline: StateFlow<List<TimelineEntry>> = _timeline.asStateFlow()
    val scenarios: StateFlow<List<Scenario>> = _scenarios.asStateFlow()

    /** Bumps when tips, routes or votes change on the backend. */
    val community: StateFlow<Long> = _community.asStateFlow()
    val link: StateFlow<LinkStatus> = _link.asStateFlow()

    /** True after a replay control call failed (shown as a short error); cleared by the next success. */
    val controlFailed: StateFlow<Boolean> = _controlFailed.asStateFlow()

    /** The scenario being replayed, null in live mode. */
    val activeScenario: StateFlow<Scenario?> =
        combine(_clock, _scenarios) { c, list ->
            c?.takeIf { it.mode == Mode.HISTORICAL }?.scenarioId?.let { id -> list.firstOrNull { it.id == id } }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** True when the backend runs live (OJP today), false in the historical replay or before the first event. */
    val isLive: Boolean get() = _clock.value?.mode == Mode.LIVE

    /** The backend clock now; the device clock until the first event arrives. */
    fun currentNow(): Instant = _clock.value?.let { displayNow(it, realClock.now() - receivedAt) } ?: realClock.now()

    fun start() {
        scope.launch { api.liveEvents().collect(::onEvent) }
        scope.launch {
            while (_scenarios.value.isEmpty()) {
                attempt { api.scenarios() }.onSuccess { _scenarios.value = it }
                if (_scenarios.value.isEmpty()) delay(SCENARIO_RETRY)
            }
        }
        scope.launch {
            while (isActive) {
                _now.value = currentNow()
                delay(tick)
            }
        }
    }

    internal fun onEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.Clock -> setClock(event.clock)
            is ServerEvent.State -> {
                setClock(event.state.clock)
                _disruptions.value = event.state.disruptions
                _timeline.value = event.state.timeline
            }
            is ServerEvent.Community -> _community.value = event.revision
            is ServerEvent.Link -> _link.value = event.status
        }
    }

    private fun setClock(clock: ClockState) {
        _clock.value = clock
        receivedAt = realClock.now()
        _now.value = clock.now
    }

    fun startScenario(id: String) = control { api.startScenario(id) }

    fun togglePlay() = control { if (_clock.value?.playing == true) api.pause() else api.play() }

    fun seek(at: Instant) = control { api.seek(at) }

    fun cycleSpeed() = control { api.speed(nextSpeed(_clock.value?.speed ?: 1.0)) }

    fun goLive() = control { api.live() }

    private fun control(call: suspend () -> ClockState) {
        scope.launch {
            attempt { call() }
                .onSuccess {
                    setClock(it)
                    _controlFailed.value = false
                }.onFailure { _controlFailed.value = true }
        }
    }

    private companion object {
        val SCENARIO_RETRY = 5.seconds
    }
}
