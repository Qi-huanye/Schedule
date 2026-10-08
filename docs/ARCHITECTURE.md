# Schedule Architecture

Local-first Android application, minSdk 26, Kotlin, Compose Material 3,
Navigation Compose, Room, Coroutines/StateFlow, serialization, OkHttp,
WorkManager. No accounts, analytics or advertisements. Optional automatic update
checks default to enabled and request public GitHub release metadata on foreground,
at most once per 24 hours, without schedule data or device identifiers.

## Directory design

```text
app/src/main/java/dev/wakeuppure/
  data/local/              Room entities, DAO, database
  data/background/         bounded private image import and Celebi/Score extraction
  data/repository/         transaction and Flow-backed repositories
  data/update/             stable GitHub release discovery and version comparison
  data/wakeup/json/        external DTO parsing and mapping
  data/wakeup/legacy/      legacy envelope handling
  data/wakeup/token/       profile, identity, request/response protocol
  data/wakeup/token/crypto/ independent tested primitives
  domain/model/           Schedule, Course, CoursePeriod, TimeSlot
  domain/usecase/         week calculation, filtering, conflicts
  ui/                    activity, navigation, ViewModel, theme
  ui/timetable/          week view and details
  ui/course/             multi-period course editor
  ui/today/              chronological occurrences
  ui/settings/           schedules, slots, appearance, import/export
  ui/update/             persisted check policy, lifecycle checks and update prompts
  ui/background/         background state, HCT roles, course display colors and settings
  notification/          opt-in notifications
  widget/                today and next-course widgets
app/src/test/             deterministic domain/parser/crypto tests
app/src/androidTest/      Room and user workflow tests
```

## Data contract

Schedule owns courses and numbered time slots. CoursePeriod owns a classroom
override so a course can change rooms across weeks. All week numbering is
relative to the Monday containing semesterStartDate; display first-day settings
reorder days but do not alter academic week numbers. Dates before semester
and after maxWeeks are shown explicitly; no invalid week course matches.

At most one current schedule, selected transactionally. Removing a current
schedule chooses another. Course+period writes and full imports are atomic.
Foreign keys cascade deletes. Versioned schema exported; no destructive fallback.
Validate section/week/day bounds and time slots before persistence.

## UI and privacy

Bottom tabs: 课表 / 今日 / 我的. Dense scrollable timetable with swipe week
navigation, per-course colors, readable details, tap/long-press editing.
Course conflicts occupy separate lanes and retain detail access. Dark/system/
light appearance; schedule-local weekend, first-day and reminder settings.
Use SAF for file import/export and Android Sharesheet for text. No storage
or phone identifiers permissions. Network is used for explicit token import and
GitHub update checks. My → About provides manual checks and an automatic-check switch.
Users can dismiss a prompt or persistently skip one version; manual checks override
the skipped version and time limit. Coroutine cancellation cancels the HTTP call.
Downloads open the canonical GitHub release page; the app does not install updates.

My → Appearance imports a local background through OpenDocument. A private PNG
and a small JSON preferences record hold the image, extracted colors and two
independent switches. Import bounds input to 32 MiB and decoded dimensions to
2048 pixels, honors all EXIF orientations and drops source metadata. Image writes
and settings replacement preserve the previous background on failure. Decoding
and palette generation run off the main thread. No image bytes leave the device.

Celebi (Wu + WSMeans), Score and HCT Tonal Spot generate complete light/dark
Material 3 roles with medium contrast through MaterialKolor utilities. Theme
following is enabled by default; course following is opt-in. Six course display
colors are stable by course ID, and never modify Course.color or Room. Removal
restores the existing theme/color behavior; background settings and images are
not part of schedule JSON backups. Opaque or near-opaque tonal panels protect
text while the image remains visible through timetable gaps and page margins.

## Delivery and validation

Research -> buildable scaffold -> Room/domain -> timetable CRUD -> offline import
-> isolated modern protocol -> exports -> today/reminders/widgets. Database v2 explicitly migrates v1 schedules without destructive reset. Unit tests cover academic
week boundaries, odd/even periods, Sunday/year rollover, conflict lanes,
external fixtures, crypto vectors and export escaping. Instrumented tests cover
Room transactions. Final emulator checks verify editing, persistence and files.
