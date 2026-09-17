# WakeUp Import Protocol

## Offline formats

1. JSON envelope: name/startDate/tableInfo/courses.
2. Legacy text prefix (including `〖来自WakeUp课程表〗`) followed by an envelope;
   courseDetailJson can contain URL-encoded JSON.
3. Native newline JSON: time table metadata, time slots, schedule metadata,
   course base array, course detail array. Resolve detail identifiers against
   course base IDs. Reject invalid references rather than silently losing data.

External DTOs are mapped to independent Schedule/Course/CoursePeriod/TimeSlot
domain records. Parse and validate completely before a single Room transaction.
Duplicate periods are collapsed; overlapping distinct courses are preserved.

## Modern token protocol

Confirmed by reading WakeUpDecoder/wakeup_share_sim.py (Apache-2.0):

- CUID: uppercase MD5 of `com.baidu` + Android ID, followed by `|0`.
- ADID: lowercase MD5 of `alpha.beta` + ID followed by XOR of its four words.
- Generate ten random alphanumeric characters; signA embeds nonce, signer
  certificate-hex MD5 and CUID, encrypted using WakeUp's nonstandard DES.
- POST `/pluto/app/antispam`; decrypt signB using nonce prefix + `#G4`, verify
  nonce and extract the ten-byte session token.
- Derive RC4 key from versionCode and session token, encrypt form-encoded
  share key, Base64 encode, sign ordered parameters and POST
  `/share_schedule/getv2`.
- Decrypt response `data` with RC4, parse `shareData`, then offline DTO mapping.

DES is NOT JCE DES: LSB-first bits, modified PC2 table, special padding and
four-hex-character-per-byte nibble representation are required.

Known synthetic vectors from the Python reference:

| Operation | Input | Output |
|---|---|---|
| CUID | all-zero synthetic Android ID | C77D5D04D94F5F56C8A0A6DC3DBF240A\|0 |
| ADID | same synthetic ID | d58a81f457529caf0e642e7f9d97e447112bd763 |
| DES | WakeUp, key @fG2SuLA | d8070687794a30fe |
| RC4 | Plaintext, key Key | bbf316e8d940af0ad3 |

The normal mode uses the local Android ID. The explicit experimental mode uses
the public all-zero compatibility identity with fixed device parameters and
versionCode 530 / versionName 6.4.0. It is off by default; there is no automatic
identity fallback and no third-party relay.

## External uncertainty

The Python source extracts package/version/channel/public-token/certificate
profile values from a client APK. A baseline for the public reference client
6.1.70 (versionCode 450) is now bundled as WakeUpProtocolProfiles. Extraction
was performed in a cache outside this repository; the APK is not shipped or
redistributed by WakeUpPure. Users can import a newer profile to override it. DeviceIdentityProvider
uses Android's Settings.Secure.ANDROID_ID without phone-state permissions;
the Python reference completed a live handshake with this baseline and a
synthetic identity on 2026-09-17; the Kotlin Android device test also reached
errNo 410004 (anti-cheat device rejection) for a synthetic probe. No TLS bypass or sensitive
logging. Both HTTP failure and protocol mismatch become readable errors.

New share-code generation is unsupported: the source documents retrieval,
not a verified upload/generation protocol. Live import is experimental and
must never be described as verified solely because crypto unit tests pass.

## Supplying a profile

The bundled baseline can attempt a handshake without importing a file;
server acceptance of the device is still required. To override it after
a protocol change, in 我的 → 导入 / 导出 → 分享口令 → 导入协议配置, select a UTF-8 JSON
file containing the fields below. These are placeholders, not a working profile:

```json
{
  "packageName": "REPLACE_WITH_PACKAGE_NAME",
  "versionCode": 1,
  "versionName": "REPLACE_WITH_VERSION_NAME",
  "channel": "REPLACE_WITH_CHANNEL",
  "publicToken": "REPLACE_WITH_PUBLIC_TOKEN",
  "certificateHexMd5": "REPLACE_WITH_32_LOWERCASE_HEX_DIGITS",
  "apiHost": "https://api.wakeup.fun"
}
```

All values must describe the same supported official client version. The
certificate field is the MD5 of the certificate's hexadecimal text as used
by the reference protocol, not the MD5 of an APK file. A valid profile cannot
be inferred from a share code. Importing a profile only validates and saves it
locally; no connection occurs until the user presses 联网获取课表. The profile
is excluded from native schedule backups. Do not put device IDs, session
tokens or user credentials in a profile or commit them.

## Compatibility status

Successful retrieval, decryption and native-format mapping were observed using
the explicit experimental path in September 2026. This is not a guarantee of
continued server support. The normal device path can return errNo 410004
(device rejected); HTTP 200 alone does not prove successful retrieval.

Offline tests use synthetic fixtures and injected transports. The optional
LiveWakeUpProtocolTest checks the live handshake using a nonexistent synthetic
code and does not request personal schedules. Official acceptance of exported
WakeUp formats remains separately unverified.

On Android 8+, ANDROID_ID is scoped to signing key, user and device. Installing
the official app on the same phone does not establish that this app reads the
same identity. No other app's private data or identity is accessed.

Sources: [WakeUpDecoder](https://github.com/airline233/WakeUpDecoder),
[Android ANDROID_ID](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID).
