package ch.ausweg.shared.api

import ch.ausweg.shared.model.ClockState
import ch.ausweg.shared.model.StateResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How the app is connected to the backend right now. */
enum class LinkStatus {
    /** Server-sent events are flowing. */
    LIVE,

    /** SSE failed; the app polls `/state` instead (no community updates until SSE is back). */
    POLLING,

    /** The backend can't be reached. */
    OFFLINE,
}

/** One message from `GET /events`, or a change of [LinkStatus]. */
sealed interface ServerEvent {
    data class Clock(
        val clock: ClockState,
    ) : ServerEvent

    data class State(
        val state: StateResponse,
    ) : ServerEvent

    /** Tips, routes or votes changed: refetch them. */
    data class Community(
        val revision: Long,
    ) : ServerEvent

    data class Link(
        val status: LinkStatus,
    ) : ServerEvent
}

/** Turns one SSE message into a [ServerEvent]; null for unknown event names or bad data. */
fun parseServerEvent(
    name: String?,
    data: String?,
): ServerEvent? {
    if (data == null) return null
    return runCatching {
        when (name) {
            "clock" -> ServerEvent.Clock(ApiJson.decodeFromString<ClockState>(data))
            "state" -> ServerEvent.State(ApiJson.decodeFromString<StateResponse>(data))
            "community" -> data.trim().toLongOrNull()?.let { ServerEvent.Community(it) }
            else -> null
        }
    }.getOrNull()
}

/** How often to poll `/state` while SSE is down. */
val POLL_INTERVAL: Duration = 3.seconds

/** Polls before SSE is tried again. */
const val POLLS_BEFORE_RETRY = 5

/**
 * [AuswegApi.events] that never gives up: when SSE fails or ends it reports [LinkStatus.POLLING] (or
 * [LinkStatus.OFFLINE]), polls `/state` [POLLS_BEFORE_RETRY] times, [pollInterval] apart, then tries SSE again.
 * Demo safety: the screens keep updating even if a proxy or a browser breaks the event stream.
 */
fun AuswegApi.liveEvents(pollInterval: Duration = POLL_INTERVAL): Flow<ServerEvent> =
    flow {
        while (true) {
            var announced = false
            attempt {
                events().collect { event ->
                    if (!announced) {
                        emit(ServerEvent.Link(LinkStatus.LIVE))
                        announced = true
                    }
                    emit(event)
                }
            }
            repeat(POLLS_BEFORE_RETRY) {
                val polled = attempt { state() }.getOrNull()
                if (polled == null) {
                    emit(ServerEvent.Link(LinkStatus.OFFLINE))
                } else {
                    emit(ServerEvent.Link(LinkStatus.POLLING))
                    emit(ServerEvent.Clock(polled.clock))
                    emit(ServerEvent.State(polled))
                }
                delay(pollInterval)
            }
        }
    }

/** [runCatching] for suspend calls that doesn't swallow cancellation. */
@Suppress("TooGenericExceptionCaught") // Network errors differ per engine (IOException, JS errors…).
suspend inline fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
