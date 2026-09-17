package dev.wakeuppure

import dev.wakeuppure.data.wakeup.token.*
import dev.wakeuppure.data.wakeup.token.crypto.WakeUpCrypto
import java.util.Base64
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class WakeUpTokenParserTest {
    @Test fun deviceRejectionIsNotMisreportedAsExpiredCode() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            WakeUpProtocol.decryptShareData("""{"errNo":410004,"errstr":"命中反作弊","data":null}""", "key")
        }
        assertTrue(error.message.orEmpty().contains("未接受当前设备"))
        assertTrue(error.message.orEmpty().contains("JSON"))
        assertFalse(error.message.orEmpty().contains("已过期"))
    }

    @Test fun importCompletesSyntheticHandshakeThroughInjectedTransport() = syntheticImport(WakeUpIdentityMode.DEVICE)
    @Test fun experimentalModeDoesNotReadRealIdentity() = syntheticImport(WakeUpIdentityMode.EXPERIMENTAL)
    private fun syntheticImport(mode: WakeUpIdentityMode) = runBlocking {
        val experimental = mode == WakeUpIdentityMode.EXPERIMENTAL
        val version = if (experimental) 530 else 6170
        var count = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            val body = buffer.readUtf8()
            if (experimental) {
                assertEquals(WakeUpCrypto.cuid("0".repeat(16)), request.header("zyb-cuid"))
                assertTrue(body.contains("vc=530"))
                assertTrue(body.contains("screensize=1080x2400"))
            }
            val json = if (count++ == 0) {
                assertEquals("/pluto/app/antispam", request.url.encodedPath)
                assertTrue(body.endsWith("&"))
                val signA = URLDecoder.decode(body.substringAfter("data=").substringBefore('&'), "UTF-8")
                val plain = WakeUpCrypto.desDecrypt(WakeUpCrypto.nativeHexDecode(signA), "@fG2SuLA").toString(Charsets.UTF_8)
                val nonce = plain.split("##")[1]
                val signB = WakeUpCrypto.nativeHexEncode(WakeUpCrypto.desEncrypt("${nonce}##0123456789".toByteArray(), nonce.take(5) + "#G4"))
                "{\"data\":\"$signB\"}"
            } else {
                assertEquals("/share_schedule/getv2", request.url.encodedPath)
                assertTrue(body.startsWith("&data="))
                val key = WakeUpCrypto.requestKey(version.toString(), "0123456789")
                val data = Base64.getEncoder().encodeToString(WakeUpCrypto.rc4("{\"shareData\":\"fixture schedule\"}".toByteArray(), key))
                "{\"errNo\":0,\"data\":\"$data\"}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val importer = WakeUpTokenImporter(DeviceIdentityProvider { check(!experimental) { "Must not read physical identity in experimental mode" }; "1234567890abcdef" }, client)
        assertEquals("fixture schedule", importer.import("test-code", WakeUpProtocolProfile("fixture",version,"fixture","fixture","fixture","0".repeat(32)), mode))
        assertEquals(2, count)
    }
    @Test(expected = IllegalArgumentException::class) fun zeroIdentityIsNeverUsedAsFallback() = runBlocking {
        WakeUpTokenImporter(DeviceIdentityProvider { "0000000000000000" }).import("test", WakeUpProtocolProfile("fixture",1,"fixture","fixture","fixture","0".repeat(32)))
        Unit
    }
    @Test fun exactPythonRequestVector() {
        val request = WakeUpProtocol.buildShareRequest("abc 123", "0123456789", 6170, listOf("vc" to "6170", "cuid" to "test|0"), 1700000000, 123456)
        assertEquals("&data=1ihTZsdmxUUOBU8%3D&vc=6170&cuid=test%7C0&nt=wifi&sign=87c69e88ccdd6bb4beb80e4f725848e7&_t_=1700000000&kakorrhaphiophobia=123456", request.body)
    }
    @Test fun decryptsNestedShareResponse() {
        val cipher = Base64.getEncoder().encodeToString(WakeUpCrypto.rc4("{\"shareData\":\"first\\nsecond\"}".toByteArray(), "test-key"))
        assertEquals("first\nsecond", WakeUpProtocol.decryptShareData("{\"errNo\":0,\"data\":{\"data\":\"$cipher\"}}", "test-key"))
        assertEquals("first\nsecond", WakeUpProtocol.decryptShareData("{\"data\":\"$cipher\"}", "test-key"))
    }
    @Test fun extractsAndValidatesHandshakeNonce() {
        val nonce = "ABCDEFGHIJ"
        val signB = WakeUpCrypto.nativeHexEncode(WakeUpCrypto.desEncrypt("${nonce}##0123456789".toByteArray(), "ABCDE#G4"))
        assertEquals("0123456789", WakeUpProtocol.tokenFromSignB(signB, nonce))
    }
    @Test(expected = IllegalArgumentException::class) fun handshakeNonceMismatchRejected() {
        val signB = WakeUpCrypto.nativeHexEncode(WakeUpCrypto.desEncrypt("XXXXXXXXXX##0123456789".toByteArray(), "ABCDE#G4"))
        WakeUpProtocol.tokenFromSignB(signB, "ABCDEFGHIJ")
    }
    @Test(expected = IllegalArgumentException::class) fun serverFailureRejected() { WakeUpProtocol.decryptShareData("{\"errNo\":403,\"data\":\"AAAA\"}", "key") }
    @Test(expected = IllegalArgumentException::class) fun absentCipherRejected() { WakeUpProtocol.decryptShareData("{}", "key") }
    @Test(expected = IllegalArgumentException::class) fun invalidProfileRejected() { WakeUpProtocolProfile("",0,"","","","").validate() }
    @Test(expected = IllegalArgumentException::class) fun httpProfileRejected() { WakeUpProtocolProfile("test",1,"1","channel","public","0".repeat(32), "http://example.com").validate() }
}
