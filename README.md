# Ausweg (working name): LauzHack 2026

A journey planner for train disruptions, for the JetBrains (Kotlin Multiplatform) × SBB «Assist customers in case of disruption» challenge.
Android + Web from one Compose Multiplatform codebase, with a Kotlin/Ktor backend.

- **Working on this repo? Read [AGENTS.md](AGENTS.md) first.** Every agent logs its work in [AGENT_LOG.md](AGENT_LOG.md).
- Spec: [`docs/spec/`](docs/spec/)

```
shared/       KMP logic: models, OJP, disruption matching, routing, tips, i18n (Android · Web · JVM)
composeApp/   Compose Multiplatform UI (Android · Web/Wasm)
server/       Ktor backend: data ingest, replay clock, OJP proxy, tips + votes, SSE
data/         raw/ SBB datasets (gitignored) · seed/ demo tips
```

## Run

Needs JDK 21 (and the Android SDK for the app).

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web
./gradlew :composeApp:assembleDebug                 # Android APK
./gradlew :server:run                               # backend on :8080
```
