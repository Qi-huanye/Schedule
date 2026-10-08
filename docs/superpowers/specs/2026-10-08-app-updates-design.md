# App update checks

Implement the accepted startup check → version comparison → download prompt flow.
Only commit locally; do not publish a Release or change the app version.

- Query `https://api.github.com/repos/Qi-huanye/Schedule/releases/latest` when the
  activity enters the foreground. Automatic checks default to enabled and run at
  most once per 24 hours, including failed attempts. Manual checks bypass this limit.
- Accept a published stable `vMAJOR.MINOR.PATCH` / `MAJOR.MINOR.PATCH` release with
  an uploaded, nonempty APK asset. Compare numeric version components; ignore
  drafts, prereleases and source-only releases. Build metadata does not affect order.
- Show the version and release notes, with actions to open its GitHub release page,
  dismiss until a later check, or skip that version. Manual checks can rediscover a
  skipped version. Open the canonical repository page in the browser for download.
- In My → About, display the actual build version, an automatic-check switch and
  a manual-check button with progress and result text. Disabling automatic checks
  suppresses their pending dialogs and results. Keep manual checks available.
- Persist the switch, last attempt and skipped version locally. Do not transmit
  schedule data, device identifiers, account information or analytics.
- Bound network timeouts; automatic failures remain silent. A manual failure must
  show a retry message. Coalesce overlapping requests; propagate cancellation.
- Keep network/parsing, update UI state and Compose controls in separate files.
  Update privacy and publishing documentation to match the new network behavior.

Verification: real HTTP fixtures with MockWebServer; persisted settings, cadence,
manual overrides and cancellation tests; Compose device tests for prompt controls;
Debug/Release unit tests, Lint and build checks. Preserve pre-existing working-tree
changes and commit only this feature.
