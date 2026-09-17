package dev.wakeuppure

import dev.wakeuppure.data.wakeup.token.WakeUpShareCode
import org.junit.Assert.*
import org.junit.Test

class WakeUpShareCodeTest {
    private val code = "0123456789abcdef0123456789abcdef"
    @Test fun acceptsBareAndFullShareMessage() {
        assertEquals(code, WakeUpShareCode.parse(code))
        assertEquals(code, WakeUpShareCode.parse("这是来自「WakeUp课程表」的课表分享，分享口令为「$code」"))
    }
    @Test fun rejectsMissingAndAmbiguousCodes() {
        assertThrows(IllegalArgumentException::class.java) { WakeUpShareCode.parse("invalid") }
        assertThrows(IllegalArgumentException::class.java) { WakeUpShareCode.parse("$code ${"a".repeat(32)}") }
    }
}
