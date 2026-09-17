package dev.wakeuppure.data.wakeup.token

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Public client metadata, not a user credential or an antispam session token.
 * Source and APK fingerprint: docs/PROTOCOL_PROFILE_PLAN.md.
 * Replacing a protocol version does not require changes in UI or crypto.
 */
object WakeUpProtocolProfiles {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseline = WakeUpProtocolProfile(
        packageName = "com.suda.yzune.wakeupschedule",
        versionCode = 450,
        versionName = "6.1.70",
        channel = "100271a",
        publicToken = "1_XPXQH3c5HRPtFHkSwi3sCCURmT25QfxM",
        certificateHexMd5 = "318c6d4f74655d4f032fb0466bcfdfbc",
        apiHost = "https://api.wakeup.fun",
    )

    fun resolve(overrideJson: String?, experimental: Boolean = false): WakeUpProtocolProfile =
        (overrideJson?.let { json.decodeFromString<WakeUpProtocolProfile>(it) }
            ?: if (experimental) baseline.copy(versionCode = 530, versionName = "6.4.0") else baseline)
            .also { it.validate() }
}
