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

The synthetic all-zero ID is a test vector only, never a business default.

## External uncertainty

Working package/version/channel/public-token/certificate profile values are
not supplied by the Python source. It extracts them from an APK. We do not
ship or extract that APK. WakeUpProtocolProfile is imported separately; absent
a profile, import returns a clear configuration error. DeviceIdentityProvider
uses Android's Settings.Secure.ANDROID_ID without phone-state permissions;
server registration expectations remain unverified. No TLS bypass or sensitive
logging. Both HTTP failure and protocol mismatch become readable errors.

New share-code generation is unsupported: the source documents retrieval,
not a verified upload/generation protocol. Live import is experimental and
must never be described as verified solely because crypto unit tests pass.
