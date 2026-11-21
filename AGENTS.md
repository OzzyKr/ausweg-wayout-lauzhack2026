# AGENTS.md

Rules for every agent (Claude, Junie, any other) and every human working in this repo.
Read this whole file before your first change. The product spec is in `docs/spec/`.

---

## 1. The one rule: log what you do in `AGENT_LOG.md`

Every agent writes a row to [`AGENT_LOG.md`](AGENT_LOG.md) for every task it does. No exceptions, including small fixes and failed attempts.

```
| Time             | Agent         | Done                          | Outcome |
| 2026-11-21 14:05 | claude/shared | Added OJP trip parser + tests | pending |
```

- **Time**: local time, `YYYY-MM-DD HH:MM`. Get it with `date '+%Y-%m-%d %H:%M'`; never guess it.
- **Agent**: `<tool>/<area>`. Tool is `claude`, `junie`, `human`, … Area is `shared`, `ui`, `server` or `infra`.
- **Done**: what you did, **very short** (aim for ≤ 10 words). One row per task, not one per file.
- **Outcome**: always write `pending`. The human reviewer changes it to `accepted`, `changed: <why>` or `rejected: <why>` when the PR is merged or closed.
- Append at the **bottom**. Never edit or delete another row; the only exception is a reviewer setting Outcome.
- Commit the log row **in the same PR** as the work it describes.
- If you hit a merge conflict in the log, keep both sides' rows (`.gitattributes` sets `merge=union` for local merges).

## 2. What we're building

**Ausweg** (working name): a journey planner for train disruptions. Android + Web are built from one Kotlin Multiplatform codebase, with a Kotlin/Ktor backend.
It answers «what should I do now?» with our own recommended route (built from SBB data and OJP) plus community routes and transfer tips.
The demo has two modes: **historical** replay (Sept 2026 data, with a simulated clock) and **live** (OJP today).

| Module | Area | What goes where |
|---|---|---|
| `shared/src/commonMain/.../shared/` | `shared` | `model/` data classes · `ojp/` OJP 2.0 XML requests and parsing · `api/` backend client (REST + SSE) · `disruption/` keyword rules, leg × disruption matching, disruption state · `routing/` dropping or re-querying trips through an affected section · `tips/` ranking (votes, age, staff endorsement) and «similar disruption» matching · `i18n/` JSON string loading with English fallback |
| `shared/src/commonTest/` | `shared` | Tests for all of the above |
| `composeApp/src/commonMain/.../app/` | `ui` | `theme/` Material 3 light and dark · `navigation/` · `components/` (leg strip, disruption card, update banner, replay bar) · `screens/` `plan`, `options`, `route` (community route), `share`, `trip` (live trip) |
| `composeApp/src/commonMain/composeResources/files/i18n/` | `ui` | `en.json` (source of truth), other `<lang>.json` files, `languages.json` |
| `composeApp/src/androidMain`, `wasmJsMain` | `ui` | Platform entry points only |
| `server/src/main/.../server/` | `server` | `ingest/` loading and joining the SBB datasets · `replay/` simulated clock · `ojp/` OJP calls (key, rate limit) · `tips/` tips and votes storage · `events/` SSE · `api/` HTTP routes |
| `data/` | `server` | `raw/` SBB datasets (gitignored, never committed) · `seed/` demo tips |
| `gradle/`, `.github/`, `config/`, root files | `infra` | Build, CI/CD, lint config |

## 3. Engineering principles

1. **Shared first.** Anything that isn't drawing pixels or calling a platform API goes in `shared/src/commonMain`. Screen state lives in shared classes that expose `StateFlow`; composables render state and send events, nothing more. No parsing, matching, ranking or time math in `composeApp`. (JetBrains scores how much logic is shared.)
2. **No hardcoded user-facing strings.** All UI text is a key in `i18n/en.json`, and English is the fallback. Disruption texts from SBB stay in their original language (DE/FR/IT/EN).
3. **Test shared logic.** Every parser, matcher, ranker and state transition in `shared` ships with unit tests in `commonTest`, in the same PR. Use small scrubbed fixtures from real data. UI tests are optional.
4. **Lint is law.** ktlint and detekt fail CI. Fix the code rather than suppress the warning. If you really must suppress, use `@Suppress("Rule")` with a one-line reason. Never loosen `config/detekt/detekt.yml` or `.editorconfig` without a human's OK.
5. **CI green before merge.** Nothing merges into `main` while any CI job is red. `main` must always build, and the historical demo must always run.
6. **Stay in your area.** Work only in your area's folders. If you must touch another area, `gradle/libs.versions.toml` or CI, say so in the PR description. One task per branch, small PRs.
7. **Simple over clever.** It's a hackathon: build the smallest thing that works for the demo. No speculative abstractions. Don't add a new dependency without one line in the PR saying why.
8. **No AI model at runtime.** Disruption messages are read with keyword rules (e.g. «Bahnersatz / bus de remplacement» → replacement bus).
9. **Data privacy.** Raw SBB data is never committed. Internal ticker (KULT) data has names and phone numbers stripped, is paraphrased, and is never shown raw in the app or in logs.
10. **Demo safety.** Historical replay is the stage demo, so never break it. Live/OJP failures fall back gracefully. Never commit the OJP API key: read it from an env var or `local.properties`.

## 4. Workflow

- **Branch per task:** `<tool>/<area>/<short-task>`, e.g. `claude/shared/ojp-parser`, `junie/ui/plan-screen`.
- **Commit messages are tagged:** `[claude] shared: add OJP trip parser`, `[junie] ui: plan screen layout`, `[human] …`.
- **PR to `main`**, filling in the PR template. A **human reviews and merges**; agents never merge their own PRs.
- **Head-to-head** (one task, two agents): branches `claude/<area>/<task>` and `junie/<area>/<task>`. Both log their work. The reviewer keeps one and marks the other `rejected: lost h2h — <why>`.
- Before pushing, run the lint and test commands below.

## 5. Commands

```bash
./gradlew ktlintFormat                              # auto-fix formatting
./gradlew ktlintCheck detekt                        # lint (CI)
./gradlew :shared:jvmTest :server:test              # unit tests (CI)
./gradlew :composeApp:assembleDebug                 # Android APK
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web app, dev server
./gradlew :server:run                               # backend on :8080 (PORT env to override)
./gradlew kotlinWasmUpgradeYarnLock                 # after changing web/npm deps (updates kotlin-js-store/)
```

Stack: Kotlin 2.2.21 · Compose Multiplatform 1.9.3 · AGP 8.12.3 · Ktor 3.3.3 · Gradle 8.14.3 · JDK 21. Versions live only in `gradle/libs.versions.toml`.
