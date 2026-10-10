# Timetable Startup Implementation Plan

> **For agentic workers:** Implement the approved design task by task in this session.
> User instructions override test-first and approval/commit checkpoints: no test
> suites, no repeated approval, and preserve all existing uncommitted work.

**Goal:** Reuse persisted week calculations and show a coherent first frame while
deferring update work and skipping unused widget calculations.

**Architecture:** Separate pure, serializable week layouts from their bounded
memory/disk cache. Publish prepared schedule state atomically, gate the expensive
root UI on both schedule and background readiness, and release the splash after
layout. Render exactly the existing cards using calculated indexes and geometry.

**Tech Stack:** Kotlin, Compose, coroutines/StateFlow, kotlinx.serialization, AtomicFile.

## Tasks

- [x] Add `domain/usecase/TimetableLayout.kt`: calculate each day's card order,
  overlap lanes, section positions and occurrence times once; retain existing
  `TimetableGhosts` and `CourseFilter.lanes` algorithms. Store course/period indexes
  and epoch-day/seconds-of-day values; exclude colours and current-time booleans.
- [x] Add `data/local/TimetableCache.kt`: hash exact source data; bound prepared
  source entries and persisted week files; verify version/key/checksum before use;
  use AtomicFile and fall back to pure calculation on invalid cache. Warm
  `current - 1 .. current + 1`, clamped to the semester, on worker dispatchers.
- [x] Update `ui/PureViewModel.kt`: retain existing raw schedule/optimistic-edit
  behaviour, and publish a nullable prepared state using `mapLatest`, so stale
  asynchronous preparation cannot overwrite newer edits. Refresh the warm weeks
  when the local date changes.
- [x] Update `ui/timetable/TimetableScreen.kt`: accept the prepared timetable;
  reuse its week layouts with `remember`; remove per-card `CourseFilter.onDate`,
  sorting and grouping. Keep the existing pager preload and visual modifiers.
- [x] Add `ui/StartupState.kt` and update `ui/PureRoot.kt`/`MainActivity.kt`: combine
  prepared schedule, appearance and initial background readiness before composing
  the expensive UI; register the picker before the gate; release splash after
  content layout; signal first draw with a posted draw callback.
- [x] Update `ui/background/BackgroundViewModel.kt`: calculate palette on Default
  and publish final palette/pixels together, keeping viewport checks and failures.
- [x] Update the update host/ViewModel: mount after first draw; lazily instantiate
  network dependencies; serialize initial cleanup before downloading. Preserve
  settings access and lifecycle-based update checks.
- [x] Update `widget/CourseWidgets.kt`: obtain nonempty instance-ID groups before
  reading schedules, then calculate only those groups.
- [x] Review new cache invalidation, stale results, dynamic highlights and startup
  frame ordering; use the requesting-code-review skill for a focused review.
- [x] Run `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew assembleDebug` and
  `git diff --check`; inspect results. Run no test tasks.
- [x] Perform a normal emulator launch and inspect the screen. Preserve phone
  APK/private data before updating the authorized device if still connected.
  Record what was actually observed; leave test coverage and speedup claims open.

## Observed result

Debug build and whitespace checks passed; independent code review findings were
resolved. The final emulator timetable screenshot matched the original below
the status bar. The installed phone build retained identical database/WAL files,
original background and preferences. One process-cold launch with existing
projection files took 1378 ms; file metadata was unchanged across that launch.
This is a single observation, not a controlled performance benchmark. No test
suites were added or run for this implementation.
