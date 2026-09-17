# Reference Research

Reviewed 2026-09-16 before application implementation.

## Sleepy

Source: https://github.com/lingion/sleepy (local read-only reference outside this repository).
License: GPL-3.0. Reviewed CourseEntity, TimeTableEntity, ScheduleParser,
ScheduleExporter and ConflictLayoutEngine. The project uses Room, Compose,
Material 3, MVVM and WorkManager. It stores repeated course occurrences with
a group identifier; WakeUpPure instead normalizes Course and CoursePeriod.

Confirmed formats: courses arrays with name/courseName, position/room, day,
startNode, step, startWeek, endWeek, type and color; legacy envelopes contain
URL-encoded courseDetailJson. tableInfo.time is stringified slot JSON;
tableInfo.timeList uses node/startTime/endTime. Week type 0/1/2 means all/odd/even.
Conflict intervals are inclusive section ranges, clustered per day. We will
implement independent column allocation so conflicting courses remain reachable.

We are not copying Sleepy's implementation. Its lessons are format facts and
behavioral requirements. If code is incorporated later it must be attributed
and the derivative distributed consistently with GPL-3.0.

## WakeUpDecoder

Source: https://github.com/airline233/WakeUpDecoder
License file: Apache-2.0. Reviewed main.py and wakeup_share_sim.py. The protocol
is pure Python except extraction of profile values from an official APK.
Kotlin crypto translation will retain attribution and the Apache license.
No official APK is included in this application repository or distribution.
See WAKEUP_PROTOCOL.md for facts, test vectors and external dependencies.

## Yngu196/Schedule

Source: https://github.com/Yngu196/Schedule
Direct git, raw and codeload access returned 404. Cached public README describes
Room/MVVM, WorkManager, reminders and RemoteViewsService widgets, and labels
the project Apache-2.0; the license text and implementation were not available
for verification. We therefore do not reuse code from this project.

README observations (not source-verified): today/next widgets, midnight and
course-boundary refresh, alarm-based reminders and a foreground service.
We will share occurrence calculations across UI/reminders/widgets, but will
not copy its foreground-service approach. Reminders use scheduled WorkManager
work and disclose that Android battery management may delay delivery.

## Compatibility Evidence

Format fixtures from documented schemas prove parsing only, not round-trip
acceptance by every official WakeUp version. Live handshake and device rejection handling have been verified; successful
modern token retrieval/decryption has not. README must distinguish tested fixtures from external compatibility.

## Additional protocol evidence (2026-09-17)

The upstream WakeUpDecoder repository also contains a public 6.1.70 reference
APK. It was downloaded to an external research cache, never this repository.
Its embedded public metadata yields versionCode 450 (not 6170, which is only
a synthetic test-vector input). The Python reference completed a live
antispam handshake and received HTTP 200 / errNo 410004 (命中反作弊) from getv2
for a synthetic probe. These results justify a bundled overridable
profile; they do not prove successful live schedule decryption. See
PROTOCOL_PROFILE_PLAN.md for the source APK fingerprint.

The upstream README requires an identity previously registered with WakeUp.
Normal ANDROID_ID access in a differently signed app does not provide that
identity on Android 8+. A verified registration/compatibility mechanism is
still missing. No zero-ID fallback or private identity extraction is used.
