# Image Background Implementation Plan

**Goal:** Add local photo backgrounds and image-derived theme/course colors.
**Architecture:** A private image repository owns persisted BackgroundSettings;
Celebi/Score extract ImageColors; a ViewModel loads bitmap/settings asynchronously;
Compose consumes complete Material color roles and an optional course palette.
**Tech stack:** existing Compose/Room, MaterialKolor utilities, AndroidX EXIF,
JUnit/Robolectric and Compose device tests.

- [x] Establish shared ImageColors/BackgroundSettings models and dependencies.
- [x] Write failing palette/contrast tests and implement Celebi + HCT generation in
  `data/background/ImageColorExtractor.kt` and `ui/background/ImageColorScheme.kt`.
- [x] Write failing image persistence/orientation/rollback tests and implement
  `data/background/BackgroundRepository.kt` with bounded file/bitmap handling.
- [x] Implement `ui/background/BackgroundViewModel.kt` and its state transitions.
- [x] Implement background renderer/settings in `ui/background/BackgroundUi.kt`,
  integrate Theme/PureRoot and guard text surfaces throughout app screens.
- [x] Apply course colors as a display override in Timetable/Today and describe
  the override in CourseEditor without changing saved course data.
- [x] Verify picker cancellation, switching, restart and removal with synthetic
  images on the emulator; run unit tests, Lint and both APK builds.
- [x] Review, apply only feature changes to the original checkout, build and install
  on the connected phone with signature/data checks; keep existing edits intact.
- [x] Record verification and a Git commit; push under the user's existing instruction.
  Do not publish a Release or tag.
