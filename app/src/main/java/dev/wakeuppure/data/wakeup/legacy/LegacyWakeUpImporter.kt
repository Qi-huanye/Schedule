package dev.wakeuppure.data.wakeup.legacy

import dev.wakeuppure.data.wakeup.json.WakeUpJsonImporter
import dev.wakeuppure.domain.model.ScheduleData

object LegacyWakeUpImporter {
    fun parse(text: String): ScheduleData {
        val start = text.indexOfFirst { it == '{' || it == '[' }
        require(start >= 0) { "分享文本未包含课表数据，请使用分享口令导入" }
        return WakeUpJsonImporter.parse(text.substring(start).trim())
    }
}
