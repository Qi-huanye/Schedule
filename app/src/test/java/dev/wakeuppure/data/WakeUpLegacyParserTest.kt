package dev.wakeuppure.data

import dev.wakeuppure.data.wakeup.legacy.LegacyWakeUpImporter
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class WakeUpLegacyParserTest {
    @Test fun realStyleEnvelope() {
        val courses = URLEncoder.encode("""[{"name":"C++","day":3,"startNode":3,"step":2,"startWeek":2,"endWeek":8,"type":2}]""", "UTF-8")
        val parsed = LegacyWakeUpImporter.parse("〖来自WakeUp课程表〗\n{\"name\":\"测试\",\"courseDetailJson\":\"$courses\"}")
        assertEquals("C++", parsed.courses.single().course.name)
    }
}
