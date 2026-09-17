package dev.wakeuppure.domain.usecase

import dev.wakeuppure.domain.model.TimeSlot
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object FixedLessonTime {
    fun end(start: String, minutes: Int): String {
        require(minutes in 1..240) { "每节课时长应为 1–240 分钟" }
        val time = LocalTime.parse(start)
        val total = time.toSecondOfDay() / 60 + minutes
        require(total < 1440) { "结束时间不能跨到次日" }
        return LocalTime.of(total / 60, total % 60).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    fun apply(slots: List<TimeSlot>, minutes: Int?): List<TimeSlot> = if (minutes == null) slots else
        slots.map { it.copy(endTime = end(it.startTime, minutes)) }
}
