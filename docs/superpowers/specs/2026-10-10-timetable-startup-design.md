# Timetable calculation cache and coherent startup

Approved in conversation on 2026-10-10: persist calculated week models, reuse them
in memory, prepare one complete first screen, and defer work unrelated to that
screen. Preserve the current card rendering, gestures, colours, time highlights,
and `beyondViewportPageCount = 1`.

## Calculated weeks

The existing `ScheduleCache` remains a snapshot of database data. A separate,
disposable cache stores week calculations: displayed days/dates, sorted slots,
visible and ghost cards in their existing order, overlap lanes, slot offsets and
start/end times. Cards reference indexes in the exact source schedule. Geometry
is expressed in slots and lane fractions, never screen pixels. Theme colours and
current-time highlights remain live UI inputs.

Cache identity includes the full serialized schedule content, algorithm version
and week. Files are atomic, bounded, checked for integrity and kept outside Android
backup. Missing, obsolete or unreadable files are recalculated. A bounded in-memory
cache reuses prepared schedules. Current and adjacent weeks are loaded/calculated
off the main thread before publishing the opening screen; other weeks are read ahead for explicit navigation and the swipe direction,
with on-demand calculation as a fallback and asynchronous persistence. No database data is changed by caches.

## Startup publication

Publish the schedule list and its prepared current timetable together. Root UI
combines this with the final initial background state and appearance setting.
Before those are ready, it registers the image picker and measures the viewport
without building the main navigation/timetable. After readiness, it builds the
full UI once. The splash is released when this content is laid out, retaining the
existing emergency timeout. Background palette calculation moves off the main
thread and palette/pixels are published together.

## Deferred work

Mount the automatic update host after the first content draw, so update networking
and download cleanup do not compete with startup. The settings page may acquire
the same activity-scoped update ViewModel on demand. Cleanup must finish before a
new download writes files. Widgets query instance IDs first and only compute content
for types actually on the launcher. Reminder scheduling is unchanged.

## Verification

The user explicitly declined test suites and boundary/smoke tests. Do not add or
run them. Compile the debug APK, review the diff and have a focused code review.
Use the existing emulator for a normal visual launch and, if the authorized USB
phone remains available, install the APK with its data preserved. Versioning and publication require a separate user request. Report observed verification limits and do
not claim a startup speedup without comparable measurements.
