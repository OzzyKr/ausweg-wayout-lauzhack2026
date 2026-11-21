## What
<!-- One or two lines. -->

## Who
<!-- Agent + area, e.g. claude/shared, junie/ui, human/server -->

## Checklist
- [ ] Commits tagged `[claude]` / `[junie]` / `[human]`
- [ ] Row(s) added to `AGENT_LOG.md`
- [ ] Logic lives in `shared/commonMain`; UI only renders state
- [ ] No hardcoded user-facing strings
- [ ] Tests for new shared logic
- [ ] `./gradlew ktlintCheck detekt :shared:jvmTest :server:test` passes locally
- [ ] Touches another area / catalog / CI? Say so here: …

## Reviewer
<!-- Set the Outcome column in AGENT_LOG.md: accepted / changed: why / rejected: why -->
