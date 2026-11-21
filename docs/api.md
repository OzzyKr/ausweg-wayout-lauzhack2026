# Backend API

Ktor server (`./gradlew :server:run`, port 8080 or `PORT`). JSON everywhere. Request/response types are the
`@Serializable` classes in `shared/src/commonMain/.../shared/model/`, so the app's `api/` client can reuse them.
Times are ISO-8601 instants (`2026-09-25T14:31:08Z`). CORS is open (no auth, no cookies).

## Modes

There is **one global clock** shared by every client.

- **LIVE** (default on start): the real clock.
- **HISTORICAL**: a simulated clock replaying a past disruption (a *scenario*). Controlled by the replay bar.

Both modes use the same pipeline: a disruption version is visible only once `publishedAt ≤ clock.now`.
Ended disruptions stay visible for 1 h («service resumed»). Active ones with no update for 24 h are dropped as stale.

## Endpoints

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/health` | | `ok` |
| GET | `/state` | `?at=<instant>` optional (debug: shows that moment, doesn't move the clock) | `StateResponse` |
| GET | `/scenarios` | | `List<Scenario>` |
| POST | `/replay/start` | `StartScenarioRequest {scenarioId, play = true, speed?}` | `ClockState` · 404 unknown id |
| POST | `/replay/play` | | `ClockState` · 409 in live mode |
| POST | `/replay/pause` | | `ClockState` · 409 in live mode |
| POST | `/replay/seek` | `SeekRequest {at}` | `ClockState` · 409 in live mode |
| POST | `/replay/speed` | `SpeedRequest {x}`: simulated seconds per real second, `0 < x ≤ 3600` | `ClockState` · 400 / 409 |
| POST | `/mode/live` | | `ClockState` |
| GET | `/events` | | Server-sent events, see below |

Coming next: `POST /plan`, `GET /stops?q=` (OJP), `/tips`, `/routes` (community).

## Server-sent events: `GET /events`

| Event | Data | When |
|---|---|---|
| `clock` | `ClockState` | on connect, on every replay/mode change, and every ~5 s as a heartbeat |
| `state` | `StateResponse` | on connect, and whenever the set of known disruption versions changes |

The server checks once per second, so several quick changes can arrive as one event. Whether a change affects
the user's route (and deserves the update banner) is decided in the app by the shared `advise()` logic.

## Types (summary)

```
ClockState    { mode: LIVE|HISTORICAL, now, playing, speed, scenarioId? }
StateResponse { clock: ClockState, disruptions: List<Disruption> }
Disruption    { id, version, publishedAt, status: ACTIVE|ENDED, reasonCode?, affectedStops: List<Int> (DIDOK),
                lines, expectedEnd?, texts: Map<lang, {title, body}>, hints: List<String> }
Scenario      { id, title, start, end, disruptionIds, suggestedFrom?, suggestedTo? }
```

## Data input (provisional)

The server reads `data/seed/events.json` (override with `AUSWEG_EVENTS`) and re-reads it whenever the file changes.
Its layout is **provisional** until the data side fixes it: see
`server/src/main/kotlin/ch/ausweg/server/ingest/ProvisionalEventsFormat.kt`. Each entry is one published version of
a disruption with its `publishedAt`. `scenarios` is optional; without it, one scenario per disruption is derived.
