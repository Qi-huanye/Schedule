package dev.wakeuppure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wakeuppure.data.wakeup.token.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in only. Never fetches another person's schedule. */
@RunWith(AndroidJUnit4::class)
class LiveWakeUpProtocolTest {
    @Test fun baselineReportsUnregisteredDeviceRejection() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveWakeUp") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val importer = WakeUpTokenImporter(AndroidDeviceIdentityProvider(context))
        try {
            importer.import("WakeUpPureSyntheticNonexistentCode", WakeUpProtocolProfiles.resolve(null))
            fail("A nonexistent synthetic code must not resolve to a schedule")
        } catch (e: IllegalArgumentException) {
            // Current upstream requires a previously registered identity. This
            // verifies error handling only, never successful schedule retrieval.
            assertTrue("Expected a readable device rejection after handshake",
                e.message.orEmpty().contains("410004") && e.message.orEmpty().contains("未接受当前设备"))
        }
    }
}
