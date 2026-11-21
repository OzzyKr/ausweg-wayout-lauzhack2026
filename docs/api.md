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
| GET | `/stops?q=<name>` | | `List<Location>`, best first · 400 without `q` |
| POST | `/plan` | `PlanRequest {from, to, departAt?, via = []}` | `PlanResponse` · 503 when OJP and the fixtures can't answer |

Coming next: `/tips`, `/routes` (community).

## Planning: `POST /plan`, `GET /stops`

Stop refs are DIDOK numbers (`"8501120"`) or SLOIDs. `departAt` defaults to the backend clock's `now` (so in
historical mode, the replayed time). The OJP key stays on the server.

1. The server searches OJP. **Historical mode:** OJP only knows the current timetable, so it searches the same
   weekday and local time within the next 7 days and moves the trips back onto the replayed date (`source: SHIFTED`,
   timetable only: realtime, cancellations and OJP notices are dropped). **Live:** `source: LIVE`, with OJP `situations`.
2. If every trip is blocked by a current disruption (shared `affects()`), it searches again through up to 3 hub
   stations that avoid the closed sections (`data/routing/hubs.json`) and sets `rerouted: true`.
3. Trips are ranked with shared `rankTrips()`: trips that get through first (earliest arrival), then blocked ones.
   The app picks one and runs `advise(trip, disruptions, clock.now, otherTrips)`.

If OJP fails or there's no key, recorded answers in `data/fixtures/ojp/` are used (`source: FIXTURE`), moved onto the
requested date. Record them by running the demo routes once with `OJP_RECORD=1` and a key. `/stops` falls back to a
name search over the scenario stations.

## Server-sent events: `GET /events`

| Event | Data | When |
|---|---|---|
| `clock` | `ClockState` | on connect, on every replay/mode change, and every ~5 s as a heartbeat |
| `state` | `StateResponse` | on connect, and whenever the set of known disruption versions changes (every scenario event is a new version, so the timeline goes out with it) |

The server checks once per second, so several quick changes can arrive as one event. Whether a change affects
the user's route (and deserves the update banner) is decided in the app by the shared `advise()` logic.

## Types (summary)

```
ClockState    { mode: LIVE|HISTORICAL, now, playing, speed, scenarioId? }
StateResponse { clock: ClockState, disruptions: List<Disruption>, timeline: List<TimelineEntry> }
Disruption    { id, version, publishedAt, status: ACTIVE|ENDED, reasonCode?, affectedStops: List<Int> (DIDOK),
                lines, expectedEnd?, expectedEndType?: APPROX|AT_LEAST|UNKNOWN, texts: Map<lang, {title, body}>,
                hints: List<String> (LINE_CLOSED blocks, NO_ALTERNATIVE), sections: List<{from, to}> (DIDOK),
                bus?: {status: ORDERED|SCHEDULED|PUBLISHED, from?, to?, eta?, count?, routes} }
TimelineEntry { disruptionId, t, source: VIA|INTERNAL|PUBLIC, kind, summary }
Scenario      { id, title, start, end, disruptionIds, suggestedFrom?, suggestedTo? }
PlanRequest   { from, to, departAt?, via: List<String> }
PlanResponse  { trips: List<Trip>, disruptions, source: LIVE|SHIFTED|FIXTURE, situations, rerouted }
```

## Data input

The server reads every `data/scenarios/*.events.json` (override the folder with `AUSWEG_SCENARIOS`; format in
`data/README.md`) and re-reads a file when it changes. Each file is one scenario (id = file name without
`.events.json`), and each of its events publishes a new version of the disruption at the event's `t`:

- INTERNAL and VIA events count too, so the app reacts when SBB knew something, not only when it was published.
  Only the paraphrased `summary` is sent (as `texts.fr.body` and in the timeline); `raw` is never read.
- Only closing kinds (`INTERRUPTION`, `DETECTED`, `SEVERITY_UP`) naming sections close the line (`LINE_CLOSED`).
  Other kinds naming sections add to them while the line is closed and are ignored otherwise.
  `RESTRICTION` replaces the sections and stops blocking (warning only). `RESTORATION` reopens the sections it names
  (all of them if it names none). `ENDED` ends the disruption.
- Buses come on top of a closed line (`LINE_CLOSED` stays). A bus event from the same place updates the current bus
  without losing its details, and a bus from another place replaces it only if it says at least as much.
- A prognosis with `type: UNKNOWN` clears `expectedEnd`.

The old provisional `data/seed/events.json` (`AUSWEG_EVENTS`) is only used when there are no scenario files.

## Environment

| Variable | Default | What |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `OJP_API_TOKEN` | `OJP_API_KEY`, then `OJP_API_TOKEN` in `.env`, then `ojp.apiKey` in `local.properties` | OJP 2.0 token (opentransportdata.swiss); without it, fixtures only. Never commit it |
| `OJP_RECORD` | off | `1` saves every OJP trip answer into `data/fixtures/ojp/` |
| `AUSWEG_DATA_DIR` | `data` | Base folder for scenarios, fixtures and hubs |
| `AUSWEG_SCENARIOS` | `data/scenarios` | Scenario folder |
| `AUSWEG_EVENTS` | `data/seed/events.json` | Old provisional events file (fallback) |
