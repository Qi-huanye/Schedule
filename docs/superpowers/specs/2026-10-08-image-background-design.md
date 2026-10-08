# Background images and generated colors

Accepted scope: an image controls the application theme; course colors have a
separate optional switch. Keep the existing light/dark/system appearance selector.
Keep the previous session's no-Release constraint and preserve unrelated edits.

- My → Appearance offers image selection/removal, a preview, image-theme and
  course-color switches. Choosing an image uses the system document picker.
- Copy and normalize the image to private storage, cap input at 32 MiB and the
  decoded image at 2048 pixels per side, and respect EXIF orientation. Replacement
  failures retain the previous image and settings; no image is uploaded.
- Extract a bounded pixel sample with Material Color Utilities Celebi (Wu +
  WSMeans), rank seed colors with Score, then generate a Tonal Spot HCT scheme.
  Use the small MaterialKolor utilities port pinned to 2.0.2, compatible with the
  project's Kotlin 2.1 toolchain, and AndroidX ExifInterface 1.4.1.
- Generate complete light/dark Material color roles and six harmonious course
  colors. Apply course colors in the display layer by stable course ID, including
  timetable and Today. Do not overwrite stored course colors or mutate exports.
- Photos appear behind the app; legible surface panels protect text, forms and
  navigation. Dialogs stay opaque. Missing/removed images fall back to the existing
  system or default theme. Color computation and image decoding run off the UI thread.
- Persist image/options/colors so restart works even if the original photo is
  deleted. Removing the background restores normal rendering. Local JSON schedule
  backups keep their existing format and do not embed personal image bytes.

Verification: image persistence, failure rollback, orientation and bounded decode;
palette determinism and contrast in light/dark modes; UI selection/toggle/removal;
unit/Lint/build checks and device validation using synthetic images. Test install
on the already-authorized connected phone while preserving its course database.
