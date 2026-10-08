package dev.wakeuppure.update

import dev.wakeuppure.data.update.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AppReleaseRepositoryTest {
    @Test fun comparesNumericVersionsAndIgnoresBuildMetadata() {
        assertTrue(isNewerVersion("v0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("1.0.0", "0.99.99"))
        assertTrue(isNewerVersion("v0.2.1+build.5", "0.2.0"))
        assertFalse(isNewerVersion("v0.2.0", "0.2.0"))
        assertFalse(isNewerVersion("0.2.0+build.2", "0.2.0+build.1"))
        assertFalse(isNewerVersion("0.1.9", "0.2.0"))
    }

    @Test fun rejectsPrereleaseAndInvalidVersionTags() {
        for (tag in listOf("v0.3.0-beta.1", "latest", "", "0.3", "01.3.0", "1.2.3/other", "99999999999999999999999.0.0")) {
            assertFalse(tag, isNewerVersion(tag, "0.2.0"))
        }
        assertFalse(isNewerVersion("v1.0.0", "unknown"))
    }

    @Test fun readsUploadedApkReleaseAndUsesCanonicalRepositoryUrl() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(releaseJson()))
            val release = AppReleaseRepository(endpoint = server.url("/releases/latest")).latest()
            assertNotNull(release)
            assertEquals("0.3.0", release!!.version)
            assertEquals("修复课表显示", release.notes)
            assertEquals("https://github.com/Qi-huanye/Schedule/releases/tag/v0.3.0", release.pageUrl)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/releases/latest", request.path)
            assertEquals("application/vnd.github+json", request.getHeader("Accept"))
            assertNull(request.getHeader("Authorization"))
            assertEquals(0L, request.bodySize)
        }
    }

    @Test fun filtersDraftsPrereleasesAndUnavailableApks() = runBlocking {
        MockWebServer().use { server ->
            val repository = AppReleaseRepository(endpoint = server.url("/latest"))
            val fixtures = listOf(
                releaseJson(draft = true), releaseJson(prerelease = true),
                releaseJson(tag = "v0.3.0-beta"), releaseJson(tag = "latest"),
                releaseJson(assetName = "source.zip"), releaseJson(assetState = "starter"),
                releaseJson(assetSize = 0), releaseJson(noAssets = true),
            )
            for (fixture in fixtures) {
                server.enqueue(MockResponse().setBody(fixture))
                assertNull(repository.latest())
            }
        }
    }

    @Test fun permitsMissingReleaseNotes() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(releaseJson(notes = null)))
            assertEquals("", AppReleaseRepository(endpoint = server.url("/latest")).latest()!!.notes)
        }
    }

    @Test fun noPublishedReleaseIsAnEmptyResult() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            assertNull(AppReleaseRepository(endpoint = server.url("/latest")).latest())
        }
    }

    @Test fun httpFailuresAndMalformedBodiesAreNotReportedAsUpToDate() = runBlocking {
        MockWebServer().use { server ->
            val repository = AppReleaseRepository(endpoint = server.url("/latest"))
            for (response in listOf(
                MockResponse().setResponseCode(403), MockResponse().setResponseCode(500),
                MockResponse().setBody("not json"), MockResponse().setBody("{}"),
            )) {
                server.enqueue(response)
                try {
                    repository.latest()
                    fail("Invalid responses must fail the check")
                } catch (_: IOException) { }
            }
        }
    }

    @Test fun cancellingCheckCancelsAnUnresponsiveHttpRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val httpCancelled = AtomicBoolean(false)
            val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
                .eventListener(object : EventListener() {
                    override fun canceled(call: Call) { httpCancelled.set(true) }
                }).build()
            val request = async(Dispatchers.Default) { AppReleaseRepository(server.url("/latest"), client).latest() }
            try {
                assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                val cancelled = withTimeoutOrNull(1_000) { request.cancelAndJoin(); true } ?: false
                assertTrue("Cancellation must not wait for the network timeout", cancelled)
                assertTrue("The underlying HTTP call must also be cancelled", httpCancelled.get())
            } finally {
                client.dispatcher.cancelAll()
                request.cancelAndJoin()
            }
        }
    }

    private fun releaseJson(
        tag: String = "v0.3.0", notes: String? = "修复课表显示",
        draft: Boolean = false, prerelease: Boolean = false,
        assetName: String = "Schedule-0.3.0.apk", assetState: String = "uploaded",
        assetSize: Long = 100, noAssets: Boolean = false,
    ) = buildJsonObject {
        put("tag_name", tag)
        put("body", notes?.let(::JsonPrimitive) ?: JsonNull)
        put("draft", draft)
        put("prerelease", prerelease)
        put("html_url", "https://untrusted.invalid/download")
        putJsonArray("assets") {
            if (!noAssets) add(buildJsonObject {
                put("name", assetName); put("state", assetState); put("size", assetSize)
            })
        }
    }.toString()
}
