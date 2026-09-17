package dev.wakeuppure

import dev.wakeuppure.data.wakeup.token.WakeUpProtocolProfiles
import org.junit.Assert.*
import org.junit.Test

class WakeUpProfileTest {
    @Test fun experimentalProfileIsExplicitAndDoesNotReplaceDeviceDefault() {
        val profile = WakeUpProtocolProfiles.resolve(null, experimental = true)
        assertEquals(530, profile.versionCode)
        assertEquals("6.4.0", profile.versionName)
        assertEquals(450, WakeUpProtocolProfiles.resolve(null).versionCode)
    }
    @Test fun firstRunHasUsableProtocolMetadata() {
        val profile = WakeUpProtocolProfiles.resolve(null)
        profile.validate()
        assertEquals("com.suda.yzune.wakeupschedule", profile.packageName)
        assertEquals(450, profile.versionCode)
        assertEquals("6.1.70", profile.versionName)
        assertEquals("https://api.wakeup.fun", profile.apiHost)
    }

    @Test fun explicitOverrideWinsOverBundledVersion() {
        val profile = WakeUpProtocolProfiles.resolve("""{
            "packageName":"test.package", "versionCode":999, "versionName":"test",
            "channel":"test", "publicToken":"synthetic-public-value",
            "certificateHexMd5":"0123456789abcdef0123456789abcdef",
            "apiHost":"https://example.com"
        }""")
        assertEquals(999, profile.versionCode)
        assertEquals("https://example.com", profile.apiHost)
    }

    @Test fun invalidOverrideDoesNotSilentlySendUsingAnotherProfile() {
        assertThrows(IllegalArgumentException::class.java) { WakeUpProtocolProfiles.resolve("{}") }
        assertThrows(IllegalArgumentException::class.java) { WakeUpProtocolProfiles.resolve("not json") }
    }
}
