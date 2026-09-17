#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${SCHEDULE_KEYSTORE_PATH:?Set the release keystore path}"
: "${SCHEDULE_KEYSTORE_PASSWORD:?Set the keystore password}"
: "${SCHEDULE_KEY_ALIAS:?Set the signing alias}"
: "${SCHEDULE_KEY_PASSWORD:?Set the signing key password}"
./gradlew --no-daemon test lintRelease assembleRelease
python3 - <<'PY'
import hashlib, json, pathlib, shutil
root = pathlib.Path('app/build/outputs/apk/release')
metadata = json.loads((root / 'output-metadata.json').read_text())
entry, = metadata['elements']
version = entry['versionName']
output = pathlib.Path('release')
output.mkdir(exist_ok=True)
name = f'Schedule-{version}.apk'
shutil.copy2(root / entry['outputFile'], output / name)
(output / 'SHA256SUMS.txt').write_text(f'{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}\n')
print(f'Release APK: {output / name}')
PY
