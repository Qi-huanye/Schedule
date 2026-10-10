# Background Raw Cache Implementation Plan

**Goal:** Restore the display background from raw pixels without PNG/WebP decoding.

**Architecture:** A dedicated display-cache class owns crop/render, atomic WebP/raw
storage and validation. The repository serializes original/cache mutations; the
ViewModel refreshes pixels for focus/viewport changes and rejects stale results.

**Tech Stack:** Kotlin, Android Bitmap/Canvas/AtomicFile, mapped ByteBuffer, coroutines.

**Verification scope:** The user explicitly requested no automated, boundary or smoke
tests. Compile the APK, review the code, and confirm ordinary background display and
restart in the emulator. Further testing belongs to the user.

- [x] Add `BackgroundDisplayCache.kt` with bounded crop/render, raw mmap reads,
  header/checksum validation, atomic writes, WebP fallback and managed cleanup.
- [x] Integrate display preparation into `BackgroundRepository.importImage`, reads,
  replacement, clear and orphan recovery. Keep the normalized PNG and uncropped
  source-reading API; app startup uses `readDisplayBitmap`.
- [x] Connect `BackgroundViewModel`, initial window metrics and root size changes.
  Refresh focus/size pixels under the existing mutex without busy-state flashes;
  discard results for an outdated image, focus or viewport.
- [x] Compile with `./gradlew assembleDebug`, review the diff, and confirm the
  existing background display and restart in the API 36 emulator. Preserve pre-existing
  startup/schedule edits and report the limits of emulator evidence.

Completed verification (2026-10-10): `assembleDebug` passed; read-only code review
completed with both findings fixed. Installed the final APK on WakeUpPure_API_36;
the existing background displayed correctly after a cold start. Final log:
`BackgroundCache: raw 1080x1920 9ms`. This measures one emulator background read,
not total startup or phone performance. No automated, boundary or smoke tests
were added or run. Previous unrelated working-tree edits were preserved.
