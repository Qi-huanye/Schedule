package dev.wakeuppure.data.wakeup

import dev.wakeuppure.data.wakeup.json.WakeUpJsonImporter
import dev.wakeuppure.data.wakeup.legacy.LegacyWakeUpImporter
import dev.wakeuppure.data.wakeup.token.WakeUpProtocolProfile
import dev.wakeuppure.data.wakeup.token.WakeUpTokenImporter
import dev.wakeuppure.domain.model.ScheduleData

class WakeUpShareRepository(private val tokenImporter: WakeUpTokenImporter) {
    fun parseLocal(text: String): ScheduleData {
        val trimmed = text.trim().removePrefix("\uFEFF")
        if (trimmed.startsWith("BEGIN:VCALENDAR")) return dev.wakeuppure.data.ics.IcsImporter.parse(trimmed)
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) WakeUpJsonImporter.parse(trimmed)
        else LegacyWakeUpImporter.parse(trimmed)
    }

    suspend fun importToken(code: String, profile: WakeUpProtocolProfile,
        mode: dev.wakeuppure.data.wakeup.token.WakeUpIdentityMode = dev.wakeuppure.data.wakeup.token.WakeUpIdentityMode.DEVICE): ScheduleData =
        WakeUpJsonImporter.parse(tokenImporter.import(dev.wakeuppure.data.wakeup.token.WakeUpShareCode.parse(code), profile, mode))
}
