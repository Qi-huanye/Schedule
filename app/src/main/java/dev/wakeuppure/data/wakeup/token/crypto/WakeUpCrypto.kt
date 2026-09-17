package dev.wakeuppure.data.wakeup.token.crypto

import java.security.MessageDigest

// Adapted from airline233/WakeUpDecoder (Apache-2.0); modified for Kotlin.
object WakeUpCrypto {
    fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun cuid(androidId: String) = md5("com.baidu$androidId").uppercase(java.util.Locale.ROOT) + "|0"
    fun adid(androidId: String): String {
        val prefix = md5("alpha.beta$androidId")
        val checksum = prefix.chunked(8).map { it.toLong(16) }.reduce { a, b -> a xor b }
        return prefix + "%08x".format(checksum)
    }
    fun requestKey(type: String, token: String): String {
        val digest = md5("[$token]@")
        val rearranged = digest.substring(17).reversed() + digest.substring(15, 17) + digest.substring(0, 15).reversed()
        val chars = (md5("@#AIjd83#@6B") + md5(type) + rearranged).toCharArray()
        repeat(3) { swap(chars, it, chars.lastIndex - it) }
        val joined = String(chars)
        val output = (joined + md5(joined)).toCharArray()
        repeat(60) { swap(output, it, output.lastIndex - it) }
        return String(output)
    }
    private fun swap(chars: CharArray, a: Int, b: Int) { val old = chars[a]; chars[a] = chars[b]; chars[b] = old }
    fun sign(base64: String, token: String) = md5("8&%d*[${md5(token)}]@$base64")
    fun rc4(data: ByteArray, key: String): ByteArray {
        val bytes = key.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty()) { "加密密钥为空" }
        val state = IntArray(256) { it }
        var j = 0
        for (i in 0..255) {
            j = (j + state[i] + (bytes[i % bytes.size].toInt() and 255)) and 255
            val old = state[i]; state[i] = state[j]; state[j] = old
        }
        var i = 0; j = 0
        return ByteArray(data.size) { index ->
            i = (i + 1) and 255; j = (j + state[i]) and 255
            val old = state[i]; state[i] = state[j]; state[j] = old
            (data[index].toInt() xor state[(state[i] + state[j]) and 255]).toByte()
        }
    }
    private fun bits(bytes: ByteArray) = IntArray(bytes.size * 8) { (bytes[it / 8].toInt() ushr (it % 8)) and 1 }
    private fun permute(bits: IntArray, table: IntArray) = IntArray(table.size) { bits[table[it]] }
    private fun rotate(bits: IntArray, amount: Int) = IntArray(bits.size) { bits[(it + amount) % bits.size] }
    private fun subkeys(key: String): List<IntArray> {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        require(keyBytes.size == 8) { "DES 密钥长度错误" }
        val initial = permute(bits(keyBytes), DesTables.PC1)
        var left = initial.copyOfRange(0, 28); var right = initial.copyOfRange(28, 56)
        return DesTables.SHIFTS.map { shift ->
            left = rotate(left, shift); right = rotate(right, shift)
            permute(left + right, DesTables.PC2)
        }
    }
    private fun block(input: ByteArray, keys: List<IntArray>): ByteArray {
        val initial = permute(bits(input), DesTables.IP)
        var left = initial.copyOfRange(0, 32); var right = initial.copyOfRange(32, 64)
        for (key in keys) {
            val expanded = permute(right, DesTables.E)
            val mixed = IntArray(48) { expanded[it] xor key[it] }
            val substituted = IntArray(32)
            repeat(8) { box ->
                val off = box * 6
                val row = mixed[off] * 2 + mixed[off + 5]
                val col = mixed[off + 1] * 8 + mixed[off + 2] * 4 + mixed[off + 3] * 2 + mixed[off + 4]
                val value = DesTables.SBOX[box * 64 + row * 16 + col]
                repeat(4) { substituted[box * 4 + it] = (value ushr (3 - it)) and 1 }
            }
            val f = permute(substituted, DesTables.P)
            val next = IntArray(32) { left[it] xor f[it] }
            left = right; right = next
        }
        val finalBits = permute(right + left, DesTables.FP)
        return ByteArray(8) { byte -> (0..7).fold(0) { sum, bit -> sum or (finalBits[byte * 8 + bit] shl bit) }.toByte() }
    }
    fun desEncrypt(plain: ByteArray, key: String): ByteArray {
        val size = (plain.size / 8 + 1) * 8
        val padded = plain.copyOf(size)
        padded[size - 1] = (size - plain.size).toByte()
        return transform(padded, subkeys(key))
    }
    fun desDecrypt(cipher: ByteArray, key: String): ByteArray {
        require(cipher.isNotEmpty() && cipher.size % 8 == 0) { "DES 密文长度错误" }
        val plain = transform(cipher, subkeys(key).reversed())
        val padding = plain.last().toInt() and 255
        require(padding in 1..8 && padding <= plain.size) { "DES 填充错误" }
        require((plain.size - padding until plain.lastIndex).all { plain[it] == 0.toByte() }) { "DES 填充错误" }
        return plain.copyOf(plain.size - padding)
    }
    private fun transform(input: ByteArray, keys: List<IntArray>): ByteArray {
        val output = ByteArray(input.size)
        for (offset in input.indices step 8) block(input.copyOfRange(offset, offset + 8), keys).copyInto(output, offset)
        return output
    }
    private fun rev4(value: Int) = ((value and 1) shl 3) or ((value and 2) shl 1) or ((value and 4) ushr 1) or ((value and 8) ushr 3)
    fun nativeHexEncode(bytes: ByteArray) = bytes.joinToString("") { "%02x%02x".format(rev4(it.toInt() and 15), rev4((it.toInt() ushr 4) and 15)) }
    fun nativeHexDecode(value: String): ByteArray {
        val text = value.trimEnd('\n', '\r')
        require(text.isNotEmpty() && text.length % 4 == 0 && Regex("(?:0[0-9a-fA-F]0[0-9a-fA-F])+").matches(text)) { "握手密文格式错误" }
        return ByteArray(text.length / 4) { i -> (rev4(text[i * 4 + 1].digitToInt(16)) or (rev4(text[i * 4 + 3].digitToInt(16)) shl 4)).toByte() }
    }
}
