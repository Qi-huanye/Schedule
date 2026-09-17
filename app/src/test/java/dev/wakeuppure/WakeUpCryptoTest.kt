package dev.wakeuppure

import dev.wakeuppure.data.wakeup.token.crypto.WakeUpCrypto
import org.junit.Assert.*
import org.junit.Test

class WakeUpCryptoTest {
    @Test fun pythonCompatibilityVectors() {
        assertEquals("C77D5D04D94F5F56C8A0A6DC3DBF240A|0", WakeUpCrypto.cuid("0000000000000000"))
        assertEquals("d58a81f457529caf0e642e7f9d97e447112bd763", WakeUpCrypto.adid("0000000000000000"))
        val encrypted = WakeUpCrypto.desEncrypt("WakeUp".toByteArray(), "@fG2SuLA")
        assertEquals("d8070687794a30fe", encrypted.joinToString("") { "%02x".format(it) })
        assertArrayEquals("WakeUp".toByteArray(), WakeUpCrypto.desDecrypt(encrypted, "@fG2SuLA"))
        assertArrayEquals(encrypted, WakeUpCrypto.nativeHexDecode(WakeUpCrypto.nativeHexEncode(encrypted)))
        assertEquals("bbf316e8d940af0ad3", WakeUpCrypto.rc4("Plaintext".toByteArray(), "Key").joinToString("") { "%02x".format(it) })
        assertEquals("750b430d5bce4eab01c189d95d08a13b6a551466a0ed5688c1351cdaee317931b561e3108f3b6f6cabea544b6282ccc231cd22941af1d9898bc5ba70303e789a", WakeUpCrypto.requestKey("6170", "0123456789"))
    }
    @Test fun alignedAndEmptyPaddingRoundTrips() {
        for (size in listOf(0, 1, 7, 8, 9, 16)) {
            val bytes = ByteArray(size) { it.toByte() }
            assertArrayEquals(bytes, WakeUpCrypto.desDecrypt(WakeUpCrypto.desEncrypt(bytes, "12345678"), "12345678"))
        }
    }
    @Test(expected = IllegalArgumentException::class) fun malformedCipherRejected() { WakeUpCrypto.desDecrypt(byteArrayOf(1), "12345678") }
    @Test(expected = IllegalArgumentException::class) fun malformedNativeHexRejected() { WakeUpCrypto.nativeHexDecode("zzzz") }
}
