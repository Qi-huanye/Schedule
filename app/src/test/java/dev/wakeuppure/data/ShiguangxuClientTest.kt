package dev.wakeuppure.data

import dev.wakeuppure.data.shiguangxu.ShiguangxuClient
import dev.wakeuppure.data.shiguangxu.ShiguangxuImportStage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ShiguangxuClientTest {
    private fun MockWebServer.reply(data: String, code: Int = 200) = enqueue(MockResponse().setBody("""{"code":$code,"data":$data}"""))

    @Test fun newTimetablePreservesOldAndSyncRetryDoesNotCreateAnotherCopy() = runTest {
        MockWebServer().use { server ->
            server.start()
            val term = """{"id":"new-local-id","termName":"示例","weekCount":20,"startDatetime":"20260831000000","lessonConfigNew":[{"lessonConfig":[{"lessonNo":1,"classBeginTime":"080000","classEndTime":"084500"}]}]}"""
            val rows = """{"rows":[{"termId":"new-local-id","courseName":"数学","courseWeeksBitSetList":[1,3],"weekBitSetList":[1],"lessonBitSetList":[1],"beginLessonNo":1,"duration":1},{"termId":"old-id","courseName":"应忽略"}]}"""
            server.reply("""{"token":"synthetic-session"}""")
            server.reply("""{"rows":[{"id":"old-id"}]}""")
            server.reply("{}")
            server.enqueue(MockResponse().setResponseCode(503))
            server.reply("""{"CourseInfo":2}""")
            server.reply("""{"rows":[{"id":"old-id"},$term]}""")
            server.reply(rows)
            val client = ShiguangxuClient(baseUrl = server.url("").toString().trimEnd('/'))
            val shared = buildJsonObject { put("id", "source-id") }
            val stages = mutableListOf<ShiguangxuImportStage>()
            client.login("86", "1234567890", "synthetic-password")
            assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { client.importShared(shared) { stages += it } } }
            val imported = client.importShared(shared) { stages += it }.single()
            assertEquals(listOf(ShiguangxuImportStage.CHECKING_TIMETABLES, ShiguangxuImportStage.CREATING_TIMETABLE,
                ShiguangxuImportStage.READING_SYNC_VERSION, ShiguangxuImportStage.READING_SYNC_VERSION,
                ShiguangxuImportStage.READING_TIMETABLE, ShiguangxuImportStage.SYNCING_COURSES,
                ShiguangxuImportStage.PREPARING_PREVIEW), stages)
            assertEquals("数学", imported.courses.single().course.name)
            val period = imported.courses.single().periods.single()
            assertEquals(1, period.startWeek)
            assertEquals(3, period.endWeek)
            assertEquals(dev.wakeuppure.domain.model.WeekType.ODD, period.weekType)
            val requests = (1..7).map { server.takeRequest() }
            val copies = requests.filter { it.path!!.endsWith("addTermByQrCode") }
            assertEquals(1, copies.size)
            val copy = Json.parseToJsonElement(copies.single().body.readUtf8()).jsonObject
            assertFalse(copy.getValue("covered").jsonPrimitive.boolean)
            assertEquals("source-id", copy.getValue("termId").jsonPrimitive.content)
            val sync = Json.parseToJsonElement(requests.last().body.readUtf8()).jsonObject
            assertEquals(1, sync.getValue("fromVersion").jsonPrimitive.int)
        }
    }

    @Test fun loginUsesEphemeralIdentityAndTokenAsSid() = runTest {
        MockWebServer().use { server ->
            server.start(); server.reply("""{"token":"synthetic-session"}"""); server.reply("""{"id":"shared"}""")
            val client = ShiguangxuClient(baseUrl = server.url("").toString().trimEnd('/'))
            client.login("1", "1234567890", "synthetic-password")
            val login = server.takeRequest()
            assertNull(login.getHeader("sid"))
            val body = Json.parseToJsonElement(login.body.readUtf8()).jsonObject
            assertEquals("1", body.getValue("areaCode").jsonPrimitive.content)
            assertFalse(body.getValue("device").jsonObject.getValue("supportPush").jsonPrimitive.boolean)
            client.preview("abcdefghijklmnopqrstuv==")
            val preview = server.takeRequest()
            assertEquals("synthetic-session", preview.getHeader("sid"))
            assertFalse(preview.body.readUtf8().contains("synthetic-password"))
            client.clear()
            assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { client.preview("abcdefghijklmnopqrstuv==") } }
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun failedLoginNeverEchoesRemoteAccountDetails() = runTest {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody("""{"code":400,"msg":"private-account-and-password","data":{}}"""))
            val client = ShiguangxuClient(baseUrl = server.url("").toString().trimEnd('/'))
            val error = assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { client.login("86", "1234567890", "synthetic-password") }
            }
            assertFalse(error.message.orEmpty().contains("private-account"))
        }
    }

    @Test fun expiredSessionAndRedirectAreRejected() = runTest {
        for (status in listOf(401, 302)) MockWebServer().use { server ->
            server.start(); server.reply("""{"token":"synthetic-session"}""")
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Location", "https://example.com"))
            val client = ShiguangxuClient(baseUrl = server.url("").toString().trimEnd('/'))
            client.login("86", "1234567890", "synthetic-password")
            assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { client.preview("abcdefghijklmnopqrstuv==") } }
            assertEquals(2, server.requestCount)
        }
    }
}
