# Background raw display cache

The user selected option C: retain the existing normalized PNG, add a display-size
WebP, and persist the display bitmap's raw pixels to bypass image decoding on
subsequent launches. Existing originals are not replaced. Original imports still
follow the current 2048-pixel normalization and metadata-removal rules.

The cache lives in `noBackupFilesDir/background-display`: derived, device-specific
data should survive normal launches without being included in cloud backups.
Its identity includes format version, source name/length/modification time, viewport
width/height and focus. Crop matches the existing centered horizontal and
top/center/bottom vertical alignment, and never upscales stored pixels. Blur,
theme and course colors remain rendering options and do not change cached pixels.

Read order is validated raw pixels, display WebP, normalized source. Raw reads
validate header, dimensions, pixel stride, payload length and checksum before
restoring an ARGB_8888 bitmap through a mapped buffer. No image decoder runs on a
raw hit. WebP or source fallback rebuilds raw once. Cache writes are atomic and
best effort: a full/unwritable cache must not prevent displaying or importing a
valid original. Failed imports preserve the previous original and caches.

Imports prepare derived files before publishing settings. Replacement and removal
reclaim managed derived files; only the current display variant is retained after
a successful read. Interrupted writes, invalid metadata and symlinks must not
lead to reading/deleting unrelated files. Existing installations lazily populate
the cache on first use. Legacy course-color clusters are derived once from the
uncropped source and marked computed, including valid empty grayscale results.
Focus/viewport updates reload without a busy-state flash;
outdated results must not overwrite a newer focus, viewport or selected image.

Verification follows the user's narrowed scope: code review, APK compilation, and
ordinary display/restart confirmation in the emulator. No automated, boundary or
smoke tests are added or run. Emulator timing is not a phone speedup measurement.
