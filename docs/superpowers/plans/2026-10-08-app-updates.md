# App Updates Implementation Plan

> Execute the accepted design in an isolated worktree, then apply only this feature
> to the original checkout for a local commit. The user has authorized implementation
> and explicitly requested options to close update prompts.

**Goal:** Detect new GitHub releases and let users control automatic prompts.

**Architecture:** An OkHttp release source parses stable APK releases. An Android
ViewModel owns persisted preferences and request state. Compose lifecycle and UI
components invoke checks, display results and open the release page.

**Tech stack:** Kotlin, existing OkHttp/kotlinx.serialization, StateFlow, Compose,
JUnit/MockWebServer/Robolectric and Android Compose tests.

## 1. Release discovery

- [x] Add `data/update/AppReleaseRepository.kt` and
  `test/.../update/AppReleaseRepositoryTest.kt`.
- [x] First run failing tests for numeric version ordering, draft/prerelease/APK
  filtering, safe release URLs, HTTP errors and malformed responses.
- [x] Implement the repository; run
  `./gradlew testDebugUnitTest --tests 'dev.wakeuppure.update.AppReleaseRepositoryTest'`.

## 2. Check policy and preferences

- [x] Add `ui/update/AppUpdateViewModel.kt` and
  `test/.../update/AppUpdateViewModelTest.kt`.
- [x] First verify failures for the 24-hour cadence, disabled checks, persisted
  skipped versions, manual overrides, silent automatic failures and cancellation.
- [x] Implement persisted state and coalesced asynchronous checks; run the targeted
  ViewModel tests using an injected release source and clock.

## 3. UI and documentation

- [x] Add `ui/update/AppUpdateUi.kt`; integrate it into `ui/PureRoot.kt`.
- [x] Add Compose device tests for the version dialog, skip/dismiss buttons and
  automatic-check switch; run on the dedicated emulator with synthetic releases.
- [x] Replace the hardcoded About version with `BuildConfig.VERSION_NAME` and
  update README privacy, `docs/RELEASING.md` and verification documentation.

## 4. Verify and commit

- [x] Run `./gradlew test lintDebug lintRelease assembleDebug assembleRelease` and
  targeted device tests. Review the feature diff and resolve material findings.
- [x] Apply the tested feature patch to the original checkout and index without
  staging existing timetable or ignore-file edits. Verify staged tree equality.
- [x] Commit locally; verify the remaining diff is the original user diff. Leave
  version/tag/release publication unchanged.

## Authorized phone test

- [x] Verify the connected phone and signing certificate; back up its APK and private data locally.
- [x] Install with `adb install -r`, compare database contents and verify manual checks plus the persisted automatic-check switch on the phone.
- [x] Restore the original switch setting and leave the About controls visible. No Release or tag is created.
