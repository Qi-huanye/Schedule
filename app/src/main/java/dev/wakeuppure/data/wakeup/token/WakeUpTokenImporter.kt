package dev.wakeuppure.data.wakeup.token

import android.os.Build
import dev.wakeuppure.data.wakeup.token.crypto.WakeUpCrypto as Crypto
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class WakeUpTokenImporter(
    private val identityProvider: DeviceIdentityProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun import(code: String, profile: WakeUpProtocolProfile,
        identityMode: WakeUpIdentityMode = WakeUpIdentityMode.DEVICE): String = withContext(Dispatchers.IO) {
        profile.validate()
        require(code.isNotBlank() && code.length <= 4096) { "分享口令为空或过长" }
        val experimental = identityMode == WakeUpIdentityMode.EXPERIMENTAL
        val androidId = if (experimental) WakeUpExperimentalIdentity.ANDROID_ID else identityProvider.androidId()
        require(Regex("[0-9a-fA-F]{16}").matches(androidId) && (experimental || androidId.any { it != '0' })) { "无法读取有效的设备标识，请检查设备设置" }
        val cuid = Crypto.cuid(androidId)
        val adid = Crypto.adid(androidId)
        val abis = if (experimental) listOf("arm64-v8a") else Build.SUPPORTED_ABIS?.toList().orEmpty()
        val common = listOf("area" to "", "screensize" to if (experimental) "1080x2400" else "", "cuid" to cuid, "os" to "android", "city" to "", "abis" to abis.joinToString(","), "channel" to profile.channel, "appBit" to if (abis.any { it.contains("64") }) "64" else "32", "vc" to profile.versionCode.toString(), "deviceId" to "", "token" to profile.publicToken, "adid" to adid, "province" to "", "pkgName" to profile.packageName, "appId" to "wakeup", "download_type" to "1", "vcname" to profile.versionName, "sdk" to if (experimental) "35" else Build.VERSION.SDK_INT.toString(), "device" to if (experimental) "Pixel 7" else Build.MODEL.orEmpty(), "brand" to if (experimental) "google" else Build.BRAND.orEmpty(), "operatorid" to "")
        val random = SecureRandom()
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val nonce = (1..10).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        val client = httpClient.newBuilder().cookieJar(SessionCookieJar()).followRedirects(false).followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
        fun post(path: String, body: String): String {
            val request = Request.Builder().url(profile.apiHost.trimEnd('/') + path).header("na__zyb_source__", "wakeup").header("zyb-cuid", cuid).header("zyb-adid", adid).post(body.toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())).build()
            try {
                return client.newCall(request).execute().use { response ->
                    require(response.isSuccessful) { "WakeUp 服务请求失败（HTTP ${response.code}）" }
                    val source = response.body?.source() ?: throw IllegalArgumentException("WakeUp 服务响应为空")
                    require(!source.request(4L * 1024 * 1024 + 1)) { "WakeUp 服务响应过大" }
                    source.readUtf8()
                }
            } catch (_: IOException) { throw IllegalArgumentException("无法连接 WakeUp 服务，请检查网络后重试") }
        }
        val signA = WakeUpProtocol.signA(cuid, profile.certificateHexMd5, nonce)
        val handshake = post("/pluto/app/antispam", WakeUpProtocol.form(listOf("data" to signA) + common) + "&")
        val token = WakeUpProtocol.tokenFromSignB(WakeUpProtocol.extractSignB(handshake), nonce)
        val request = WakeUpProtocol.buildShareRequest(code.trim(), token, profile.versionCode, common, System.currentTimeMillis() / 1000, System.nanoTime() / 1_000_000)
        WakeUpProtocol.decryptShareData(post("/share_schedule/getv2", request.body), request.rc4Key)
    }
    private class SessionCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) {
                this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                this.cookies.add(cookie)
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
            return cookies.filter { it.matches(url) }
        }
    }
}
