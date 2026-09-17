# Schedule Architecture

Local-first Android application, minSdk 26, Kotlin, Compose Material 3,
Navigation Compose, Room, Coroutines/StateFlow, serialization, OkHttp,
WorkManager. No accounts, analytics, advertisements or startup requests.

## Directory design

```text
app/src/main/java/dev/wakeuppure/
  data/local/              Room entities, DAO, database
  data/repository/         transaction and Flow-backed repositories
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
or phone identifiers permissions. Network occurs only after explicit token import.

## Delivery and validation

Research -> buildable scaffold -> Room/domain -> timetable CRUD -> offline import
-> isolated modern protocol -> exports -> today/reminders/widgets. Database v2 explicitly migrates v1 schedules without destructive reset. Unit tests cover academic
week boundaries, odd/even periods, Sunday/year rollover, conflict lanes,
external fixtures, crypto vectors and export escaping. Instrumented tests cover
Room transactions. Final emulator checks verify editing, persistence and files.
