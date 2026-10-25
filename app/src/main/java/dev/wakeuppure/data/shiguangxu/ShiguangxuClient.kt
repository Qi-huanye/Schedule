package dev.wakeuppure.data.shiguangxu

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import dev.wakeuppure.domain.model.ScheduleData

enum class ShiguangxuImportStage { CHECKING_TIMETABLES, CREATING_TIMETABLE, READING_SYNC_VERSION, READING_TIMETABLE, SYNCING_COURSES, PREPARING_PREVIEW }

/** One in-memory import session. No cookies, logging, persistent identifiers or credentials. */
class ShiguangxuClient(
    client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.weilaizhushou.com",
) {
    private val http = client.newBuilder().cache(null).cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .callTimeout(30, TimeUnit.SECONDS).build()
    private var sid: String? = null
    private var copiedTermId: String? = null
    private var originalIds: Set<String> = emptySet()
    fun clear() { sid = null; copiedTermId = null; originalIds = emptySet() }

    suspend fun login(areaCode: String, mobile: String, password: String) {
        clear()
        require(Regex("[0-9]{1,4}").matches(areaCode) && Regex("[0-9]{5,20}").matches(mobile) && password.isNotEmpty()) {
            "时光序导入失败：请检查国家码、手机号和密码"
        }
        val tag = UUID.randomUUID().toString().replace("-", "").take(16)
        val result = post("/base/user/v2/login", buildJsonObject {
            put("areaCode", areaCode); put("mobile", mobile); put("password", password)
            put("appType", "1"); put("type", "1")
            putJsonObject("device") {
                put("deviceType", "1"); put("appVersion", "4.21.0"); put("deviceTag", tag); put("deviceTagApp", tag)
                put("isRoot", false); put("ip", "0.0.0.0"); put("channel", "xiaomi"); put("channelType", "SGX")
                put("supportPush", false); put("pushDevices", JsonArray(emptyList())); put("osVersion", 36)
                put("deviceModel", "Android"); put("channelName", "android"); put("oaid", "")
            }
        })
        sid = result["token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("时光序导入失败：登录未返回有效会话")
    }

    suspend fun preview(code: String): JsonObject {
        require(sid != null) { "时光序导入失败：请先登录" }
        val clean = code.trim()
        require(clean.length in 16..4096 && clean.none { it.isWhitespace() }) { "时光序导入失败：请扫描时光序导出的课表二维码" }
        return post("/base/syllabus/v3/getTermByQrCode", buildJsonObject { put("qrCodeString", clean) })
    }

    /** Adds a shared timetable; never requests replacement of the account's existing timetable. */
    suspend fun importShared(term: JsonObject, onStage: (ShiguangxuImportStage) -> Unit = {}): List<ScheduleData> {
        require(sid != null) { "时光序导入失败：请重新登录" }
        val termId = term["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("时光序导入失败：二维码缺少课表标识")
        if (copiedTermId != termId) {
            onStage(ShiguangxuImportStage.CHECKING_TIMETABLES)
            val before = post("/base/syllabus/v2/getTermList")["rows"]?.jsonArray
                ?: throw IllegalArgumentException("时光序导入失败：未获取到原有学期信息")
            originalIds = before.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
            onStage(ShiguangxuImportStage.CREATING_TIMETABLE)
            try {
                post("/base/syllabus/v3/addTermByQrCode", buildJsonObject { put("termId", termId); put("covered", false) })
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                throw IllegalArgumentException("时光序导入失败：新建请求未确认完成，请先在时光序检查是否已新增课表，再决定重试")
            }
            copiedTermId = termId
        }
        onStage(ShiguangxuImportStage.READING_SYNC_VERSION)
        val versions = post("/service/user/getBusinessSyncVersion")
        val version = versions["CourseInfo"]?.jsonPrimitive?.longOrNull
            ?: throw IllegalArgumentException("时光序导入失败：源账号课表已更新，但未获取到课程同步版本，请重试")
        onStage(ShiguangxuImportStage.READING_TIMETABLE)
        val terms = post("/base/syllabus/v2/getTermList")["rows"]?.jsonArray
            ?: throw IllegalArgumentException("时光序导入失败：未获取到学期信息")
        onStage(ShiguangxuImportStage.SYNCING_COURSES)
        val rows = post("/base/syllabus/sync-course", buildJsonObject { put("fromVersion", 1); put("toVersion", version) })["rows"]?.jsonArray
            ?: throw IllegalArgumentException("时光序导入失败：未获取到课程")
        val added = terms.map { it.jsonObject }.filter { it.getValue("id").jsonPrimitive.content !in originalIds }
        require(added.size == 1) { "时光序导入失败：无法确定新建课表，请在时光序中检查" }
        val target = added.single()
        val id = target.getValue("id").jsonPrimitive.content
        val courses = rows.filter { it.jsonObject["termId"]?.jsonPrimitive?.contentOrNull == id }
        require(courses.isNotEmpty()) { "时光序导入失败：源账号课表已更新，但课程同步为空，请在时光序中检查后重试" }
        onStage(ShiguangxuImportStage.PREPARING_PREVIEW)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            listOf(ShiguangxuImporter.parse(target, JsonArray(courses)))
        }
    }

    private suspend fun post(path: String, body: JsonObject? = null): JsonObject {
        val request = Request.Builder().url(baseUrl + path).header("channel", "xiaomi")
            .header("channel-type", "SGX").header("version", "4.21.0").header("accept-language", "zh-CN")
            .apply { sid?.let { header("sid", it) } }
            .post((body?.toString() ?: "").toRequestBody("application/json;charset=UTF-8".toMediaType())).build()
        val text = suspendCancellableCoroutine<String> { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalArgumentException("时光序导入失败：网络连接失败，请检查网络后重试"))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            require(it.isSuccessful) { if (it.code == 401) "时光序导入失败：登录已失效，请重新登录" else "时光序导入失败：服务暂不可用（${it.code}）" }
                            val source = it.body?.source() ?: error("empty response")
                            require(!source.request(5_000_001)) { "时光序导入失败：响应过大" }
                            source.readUtf8()
                        }
                        if (continuation.isActive) continuation.resume(value)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(if (e is IllegalArgumentException) e else IllegalArgumentException("时光序导入失败：无法读取响应"))
                    }
                }
            })
        }
        val root = try { Json.parseToJsonElement(text).jsonObject }
            catch (_: Exception) { throw IllegalArgumentException("时光序导入失败：服务返回格式异常") }
        require(root["code"]?.jsonPrimitive?.intOrNull == 200) {
            // Never echo server messages, which may contain submitted account information.
            if (path.endsWith("/login")) "时光序导入失败：登录未成功，请检查账号密码；如需验证码，请先在时光序中处理"
            else "时光序导入失败：请求未成功，二维码可能过期或登录已失效"
        }
        return root["data"] as? JsonObject ?: throw IllegalArgumentException("时光序导入失败：服务响应缺少数据")
    }
}
