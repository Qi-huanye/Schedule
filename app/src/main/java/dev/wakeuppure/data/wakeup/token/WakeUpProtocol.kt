package dev.wakeuppure.data.wakeup.token

import dev.wakeuppure.data.wakeup.token.crypto.WakeUpCrypto as Crypto
import java.net.URLEncoder
import java.util.Base64
import kotlinx.serialization.json.*

// Adapted from airline233/WakeUpDecoder (Apache-2.0); modified for Kotlin.
object WakeUpProtocol {
    data class ShareRequest(val body: String, val rc4Key: String)
    fun formEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("*", "%2A").replace("%7E", "~")
    fun form(items: List<Pair<String, String>>) = items.joinToString("&") { (key, value) -> "$key=${formEncode(value)}" }
    fun signA(cuid: String, certificateHexMd5: String, nonce: String): String {
        require(Regex("[A-Za-z0-9]{10}").matches(nonce)) { "握手随机数无效" }
        return Crypto.nativeHexEncode(Crypto.desEncrypt("8&%d*##$nonce##$certificateHexMd5##$cuid".toByteArray(Charsets.UTF_8), "@fG2SuLA"))
    }
    fun tokenFromSignB(signB: String, nonce: String): String {
        require(nonce.length == 10) { "握手随机数无效" }
        val plain = Crypto.desDecrypt(Crypto.nativeHexDecode(signB), nonce.take(5) + "#G4")
        require(plain.size >= 22 && String(plain, 0, 10, Charsets.ISO_8859_1) == nonce) { "服务端握手校验失败，请更新协议配置" }
        return String(plain, 12, 10, Charsets.ISO_8859_1)
    }
    fun extractSignB(response: String): String {
        val obj = parseResponse(response)
        val data = obj["data"]
        return string(data) ?: string((data as? JsonObject)?.get("data")) ?: string((obj["result"] as? JsonObject)?.get("data")) ?: throw IllegalArgumentException("服务端未返回握手数据")
    }
    fun buildShareRequest(code: String, token: String, versionCode: Int, common: List<Pair<String, String>>, timestamp: Long, monotonicMs: Long): ShareRequest {
        val key = Crypto.requestKey(versionCode.toString(), token)
        val data = Base64.getEncoder().encodeToString(Crypto.rc4(("key=" + formEncode(code)).toByteArray(Charsets.UTF_8), key))
        val parameters = listOf("data" to data) + common + ("nt" to "wifi")
        val signed = parameters + listOf("_t_" to timestamp.toString(), "kakorrhaphiophobia" to monotonicMs.toString())
        val joined = signed.map { (k, v) -> "$k=$v" }.sorted().joinToString("")
        val sign = Crypto.sign(Base64.getEncoder().encodeToString(joined.toByteArray(Charsets.UTF_8)), token)
        return ShareRequest("&${form(parameters)}&sign=$sign&_t_=$timestamp&kakorrhaphiophobia=$monotonicMs", key)
    }
    fun decryptShareData(response: String, key: String): String {
        val obj = parseResponse(response)
        val data = obj["data"]
        val encrypted = string(data) ?: string((data as? JsonObject)?.get("data")) ?: throw IllegalArgumentException("服务端未返回课表密文")
        val plain = try { Crypto.rc4(Base64.getDecoder().decode(encrypted), key).toString(Charsets.UTF_8) } catch (_: Exception) { throw IllegalArgumentException("课表密文格式错误") }
        val decoded = try { Json.parseToJsonElement(plain) as? JsonObject } catch (_: Exception) { null }
        return decoded?.get("shareData")?.let(::string)?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("课表解密失败或数据为空，请检查口令与协议配置")
    }
    private fun string(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
    private fun parseResponse(response: String): JsonObject {
        val obj = try { Json.parseToJsonElement(response) as? JsonObject } catch (_: Exception) { null }
        requireNotNull(obj) { "服务端响应格式错误" }
        val err = (obj["errNo"] as? JsonPrimitive)?.intOrNull
        require(err != 410004) { "WakeUp 服务未接受当前设备（410004）。请改用 JSON 或旧版分享文本导入。" }
        require(err == null || err == 0) { "WakeUp 服务拒绝请求（错误码 $err），请检查口令、设备和协议配置" }
        return obj
    }
}
