---
name: shared-first-feature
description: How to add or change a user-facing feature in Ausweg the shared-first way (shared state + logic + tests, then Compose UI, then i18n in 4 languages). Use for any change that touches a screen, a state holder, the API contract or disruption/advice logic.
---

# Add a feature, shared first

Ausweg is one Kotlin Multiplatform codebase: `shared` (Android · JVM · Wasm), `composeApp` (Compose Multiplatform,
Android + Web) and `server` (Ktor, depends on `shared`). Background: [`docs/architecture.md`](../../../docs/architecture.md),
rules: [`AGENTS.md`](../../../AGENTS.md). Follow these steps in order.

## 1. Branch and scope
- Branch `<agent>/<area>/<short-task>` from `main` (e.g. `claude/shared/wait-card`). One task per branch.
- Area = where most of the change lands. If you also touch another area, say so in the PR.

## 2. Contract first (`shared/src/commonMain/.../model/`)
- Request and response types are `@Serializable` classes shared by the server and the app. **Add fields with
  defaults; never rename or remove a field** (three consumers decode them). Add a case to `model/ContractsTest.kt`
  showing that old JSON still decodes.
- Server endpoint? Document it in `docs/api.md` in the same PR.

## 3. Logic as pure functions
- Put it in the matching package: `disruption/` (matching, advice, keyword rules), `routing/` (options, reroute,
  time shift), `tips/` (ranking, leg strips, route edits), `i18n/` (formatting), `ojp/` (XML).
- Take `now: Instant` as a parameter. Never read the device clock: the server's replay clock is the truth.
- Match stations by DIDOK number (`StopRef.didok`, `didokOf()`), never by name.
- No AI model, no new dependency without one line of justification in the PR.

## 4. Screen state (`shared/.../state/`)
- One holder per screen exposes `StateFlow<XxxUi>` and plain methods for events. Wire it in `AuswegApp`.
- Network goes through `AuswegApi`; tests use `state/FakeApi.kt`. Platform storage goes through `Settings`
  (`MemorySettings` in tests).

## 5. Tests in `shared/src/commonTest` (same PR)
- Every parser, matcher, ranker and state transition gets a test. Reuse fixtures: `disruption/Fx.kt` (Puidoux),
  `ojp/OjpFixtures.kt`, `ojp/OjpSituationFixtures.kt`.
- A bug fix comes with a test that fails on the old code.

## 6. UI (`composeApp/src/commonMain/.../app/`)
- Composables `collectAsState()` and call holder methods. **No parsing, matching, ranking or time math here.**
- Reuse `components/` (`LegStripView`, disruption cards, `Votes`, `Replay`) and `theme/` colours; check light and
  dark, the phone layout and the wide web layout (≥ 900 dp).
- Only touch `androidMain` / `wasmJsMain` for a real platform API, and keep it tiny.

## 7. Strings
- Every user-facing text is a key: add it to `composeResources/files/i18n/en.json` **and** `fr.json`, `de.json`,
  `it.json`, with the same `{placeholders}`. Use `t("literal.key")` (literal keys only: the check test finds them).
- SBB's live disruption texts are shown as published, never translated.

## 8. Gate, log, PR
```bash
./gradlew ktlintFormat
./gradlew ktlintCheck detekt :shared:jvmTest :server:test
./gradlew :composeApp:assembleDebug :composeApp:compileKotlinWasmJs   # if you touched UI
```
- Fix lint findings; don't suppress them (`@Suppress("Rule")` + reason only as a last resort).
- Append one row to `AGENT_LOG.md` (time from `date '+%Y-%m-%d %H:%M'`, `<agent>/<area>`, ≤ 10 words, `pending`).
- Commit as `[<agent>] <area>: <what>`, open a PR with the template. A human reviews and merges.
- Never break the historical replay: it is the stage demo.
