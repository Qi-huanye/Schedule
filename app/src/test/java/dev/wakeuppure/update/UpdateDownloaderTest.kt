package dev.wakeuppure.update

import dev.wakeuppure.data.update.UpdateException
import dev.wakeuppure.data.update.fetchVerifiedApk
import dev.wakeuppure.data.update.parseChecksum
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.MessageDigest

class UpdateDownloaderTest {
    @get:Rule val folder = TemporaryFolder()
    private val name = "Schedule-0.4.0.apk"
    private val apk = ByteArray(300_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private val client = OkHttpClient()

    @Test fun parsesTheChecksumForTheExactAssetName() {
        val zeros = "0".repeat(64)
        val sums = "$sha  $name\n$zeros *Other.apk\n"
        assertEquals(sha, parseChecksum(sums, name))
        assertEquals(zeros, parseChecksum(sums, "Other.apk"))
        assertNull(parseChecksum(sums, "Schedule.apk"))
        assertNull(parseChecksum("not-a-hash  $name", name))
    }

    @Test fun keepsTheApkOnlyAfterTheChecksumMatches() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("$sha  $name\n"))
            server.enqueue(MockResponse().setBody(Buffer().write(apk)))
            val progress = mutableListOf<Pair<Long, Long>>()
            val file = fetchVerifiedApk(client, server.url("/apk").toString(), server.url("/sums").toString(), name, 0, folder.root) { read, total ->
                progress += read to total
            }
            assertArrayEquals(apk, file.readBytes())
            assertEquals(apk.size.toLong() to apk.size.toLong(), progress.last())
            assertEquals(listOf(name), folder.root.list()!!.toList())
            assertEquals("/sums", server.takeRequest().path)
            assertEquals("/apk", server.takeRequest().path)
        }
    }

    @Test fun mismatchedOrMissingChecksumsLeaveNothingBehind() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("${"0".repeat(64)}  $name\n"))
            server.enqueue(MockResponse().setBody(Buffer().write(apk)))
            assertUpdateFailure("安装包校验失败，请重试") { fetch(server) }
            assertEquals(0, folder.root.list()!!.size)
            server.enqueue(MockResponse().setBody("$sha  Other.apk\n"))
            assertUpdateFailure("没有找到安装包校验值") { fetch(server) }
            assertEquals(0, folder.root.list()!!.size)
        }
    }

    @Test fun unsafeNamesAreRejectedBeforeAnyRequest() = runBlocking {
        MockWebServer().use { server ->
            assertUpdateFailure("安装包名称无效") {
                fetchVerifiedApk(client, server.url("/apk").toString(), server.url("/sums").toString(), "../evil.apk", 0, folder.root) { _, _ -> }
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun httpErrorsAreNetworkFailures() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            try {
                fetch(server)
                fail("A missing checksum file must fail")
            } catch (e: IOException) {
                assertFalse(e is UpdateException)
            }
        }
    }

    private suspend fun fetch(server: MockWebServer) =
        fetchVerifiedApk(client, server.url("/apk").toString(), server.url("/sums").toString(), name, 0, folder.root) { _, _ -> }

    private suspend fun assertUpdateFailure(message: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected: $message")
        } catch (e: UpdateException) {
            assertEquals(message, e.message)
        }
    }
}
