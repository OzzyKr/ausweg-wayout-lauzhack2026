# AGENT_LOG

Every agent appends one row per task. See [AGENTS.md §1](AGENTS.md#1-the-one-rule-log-what-you-do-in-agent_logmd).
Outcome: `pending` → set by the human reviewer to `accepted` / `changed: why` / `rejected: why`.

| Time | Agent | Done | Outcome |
|---|---|---|---|
| 2026-11-21 14:13 | claude/infra | Wrote AGENTS.md, AGENT_LOG.md, PR template | accepted |
| 2026-11-21 14:13 | claude/infra | Empty KMP scaffold (shared, composeApp, server), ktlint + detekt | accepted |
| 2026-11-21 14:13 | claude/infra | GitHub Actions CI (lint, tests, Android, web, server) + CD | accepted |
| 2026-11-21 15:15 | claude/server | Fixed timeline.py file lookup for raw subfolders | pending |
| 2026-11-21 15:15 | claude/server | Puidoux–Vauderens scenario JSON + validate.py | pending |
| 2026-11-21 15:19 | claude/server | Merged data README, format doc, removed duplicate pack | pending |
| 2026-11-21 15:37 | claude/server | Untracked internal SBB PDF; fixed Renens–Morges scenario from source | pending |
| 2026-11-21 15:53 | claude/server | Official station names + station check in validate.py | pending |
| 2026-11-21 16:45 | claude/server | DIDOK station numbers in scenarios (stationIds) | pending |
| 2026-11-21 16:46 | claude/server | pitch_stats.py: pitch numbers recomputed from VIA | pending |
