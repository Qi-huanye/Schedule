# WakeUpPure Implementation Plan

Implement inline with small build/test checkpoints; user authorized continued
execution through a runnable APK. Documents describe researched scope, not
completed features.

- [x] Inspect references and record licensing and protocol uncertainty.
- [ ] Scaffold Kotlin DSL Gradle Wrapper, Compose, Room and Navigation; assemble.
- [ ] Domain models, Room transactions and deterministic week/filter tests.
- [ ] Timetable layout, course CRUD, schedule and slot configuration; assemble.
- [ ] External WakeUp JSON/legacy DTO mapping and parser fixture tests.
- [ ] Kotlin protocol/crypto port and Python reference vectors; no live claims.
- [ ] JSON, legacy, ICS and native backup exports with SAF.
- [ ] Today view, opt-in WorkManager notifications and two widgets.
- [ ] Repository/instrumented tests, emulator workflows, screenshots and README.
- [ ] Final `./gradlew test assembleDebug lintDebug`, APK install/run and review.
