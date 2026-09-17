package dev.wakeuppure.domain.usecase

import dev.wakeuppure.domain.model.TimeSlot
import dev.wakeuppure.domain.model.defaultTimeSlots
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TimeSlotPlanner {
    fun resize(current: List<TimeSlot>, count: Int, occupiedEnd: Int): List<TimeSlot> {
        require(count in 1..30) { "每天可设置 1–30 节课" }
        require(count >= occupiedEnd) { "已有课程安排到第 $occupiedEnd 节，请先调整这些课程" }
        if (count <= current.size) return current.take(count)
        val result = current.toMutableList()
        var end = runCatching { LocalTime.parse(current.last().endTime).toSecondOfDay() / 60 }
            .getOrElse { throw IllegalArgumentException("请先修正最后一节的结束时间") }
        val remaining = count - current.size
        require(1439 - end >= remaining) { "当天没有足够时间，请先调整已有作息时间" }
        val defaults = defaultTimeSlots()
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        while (result.size < count) {
            val left = count - result.size
            val suggested = defaults.getOrNull(result.size)
            val suggestedStart = suggested?.let { LocalTime.parse(it.startTime).toSecondOfDay() / 60 }
            val suggestedEnd = suggested?.let { LocalTime.parse(it.endTime).toSecondOfDay() / 60 }
            if (suggestedStart != null && suggestedEnd != null && suggestedStart >= end &&
                1439 - suggestedEnd >= (left - 1) * 55) {
                result.add(suggested)
                end = suggestedEnd
            } else {
                val budget = (1439 - end) / left
                val gap = if (budget >= 55) 10 else 0
                val duration = minOf(45, budget - gap)
                val start = end + gap
                end = start + duration
                result.add(TimeSlot(result.size + 1, LocalTime.of(start / 60, start % 60).format(fmt),
                    LocalTime.of(end / 60, end % 60).format(fmt)))
            }
        }
        return result
    }
}
