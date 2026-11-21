# AGENT_LOG

Every agent appends one row per task. See [AGENTS.md §1](AGENTS.md#1-the-one-rule-log-what-you-do-in-agent_logmd).
Outcome: `pending` → set by the human reviewer to `accepted` / `changed: why` / `rejected: why`.

| Time | Agent | Done | Outcome |
|---|---|---|---|
| 2026-11-21 14:13 | claude/infra | Wrote AGENTS.md, AGENT_LOG.md, PR template | accepted |
| 2026-11-21 14:13 | claude/infra | Empty KMP scaffold (shared, composeApp, server), ktlint + detekt | accepted |
| 2026-11-21 14:13 | claude/infra | GitHub Actions CI (lint, tests, Android, web, server) + CD | accepted |
| 2026-11-21 15:07 | claude/server | Replay clock, events ingest, /state, replay API, SSE | accepted |
| 2026-11-21 15:15 | claude/server | Fixed timeline.py file lookup for raw subfolders | accepted |
| 2026-11-21 15:15 | claude/server | Puidoux–Vauderens scenario JSON + validate.py | accepted |
| 2026-11-21 15:19 | claude/server | Merged data README, format doc, removed duplicate pack | accepted |
| 2026-11-21 15:24 | claude/shared | OJP 2.0 trip/stop requests + parser, journey models | accepted |
| 2026-11-21 15:37 | claude/server | Untracked internal SBB PDF; fixed Renens–Morges scenario from source | accepted |
| 2026-11-21 15:53 | claude/server | Official station names + station check in validate.py | accepted |
| 2026-11-21 16:45 | claude/server | DIDOK station numbers in scenarios (stationIds) | accepted |
| 2026-11-21 16:46 | claude/server | pitch_stats.py: pitch numbers recomputed from VIA | accepted |
| 2026-11-21 16:53 | claude/shared | OJP fixtures: official station names and DIDOK numbers | accepted |
| 2026-11-21 17:00 | claude/server | Review #4: merge main, sections list, advise sketch, log outcomes | changed: fit server contract |
| 2026-11-21 17:31 | claude/shared | Advice contract, Disruption sections/bus, affects/advise stubs | pending |
| 2026-11-21 17:42 | claude/shared | Implement affects() and advise() + 26 tests | pending |
| 2026-11-21 18:30 | claude/server | Scenario ingest, timeline, OJP /plan + /stops, rerouting | accepted |
| 2026-11-21 18:41 | claude/server | Community tips, routes, votes (SQLite) + ranking | accepted |
| 2026-11-21 18:49 | claude/server | Review #7: closing kinds only, rerouted needs new trips | accepted |
| 2026-11-21 19:37 | claude/server | #8 into main, scenario stop names, AGENTS.md table | accepted |
| 2026-11-21 20:25 | claude/shared | Richer OJP situation parsing + stop-event request | pending |
| 2026-11-21 20:35 | claude/shared | OJP situation → Disruption with keyword rules + tests | pending |
| 2026-11-21 21:05 | claude/server | Live /plan: OJP situations become disruptions | pending |
| 2026-11-21 21:41 | claude/shared | Review #12: own log row back to pending | pending |
| 2026-11-21 21:42 | claude/shared | Review #13: shorter station name inside longer one | pending |
| 2026-11-21 21:43 | claude/shared | Review #14: express through closure warns, not dropped | pending |
