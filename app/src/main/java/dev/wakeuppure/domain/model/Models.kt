package dev.wakeuppure.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class WeekType { ALL, ODD, EVEN }

@Serializable
data class Schedule(
    val id: Long = 0, val name: String, val semesterStartDate: String,
    val maxWeeks: Int = 20, val current: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis(),
    val showWeekend: Boolean = true, val firstDay: Int = 1,
    val reminderMinutes: Int? = null,
    val fixedLessonMinutes: Int? = null, val colorPalette: String = "custom"
)

@Serializable
data class Course(
    val id: Long = 0, val scheduleId: Long = 0, val name: String,
    val teacher: String = "", val classroom: String = "",
    val color: String = "#C1E8DE", val note: String = ""
)

@Serializable
data class CoursePeriod(
    val id: Long = 0, val courseId: Long = 0, val dayOfWeek: Int = 1,
    val startSection: Int = 1, val endSection: Int = 2,
    val startWeek: Int = 1, val endWeek: Int = 16,
    val weekType: WeekType = WeekType.ALL, val classroom: String = ""
)

@Serializable
data class TimeSlot(val section: Int, val startTime: String, val endTime: String)

@Serializable
data class CourseWithPeriods(val course: Course, val periods: List<CoursePeriod>)

@Serializable
data class ScheduleData(val schedule: Schedule, val courses: List<CourseWithPeriods> = emptyList(),
    val timeSlots: List<TimeSlot> = defaultTimeSlots())

@Serializable
data class Backup(val format: String = "WakeUpPure", val version: Int = 1,
    val schedules: List<ScheduleData>, val appearance: String = "system")

fun defaultTimeSlots(): List<TimeSlot> = listOf(
    "08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:45", "10:55" to "11:40",
    "14:00" to "14:45", "14:55" to "15:40", "16:00" to "16:45", "16:55" to "17:40",
    "19:00" to "19:45", "19:55" to "20:40", "20:50" to "21:35", "21:45" to "22:30"
).mapIndexed { index, (start, end) -> TimeSlot(index + 1, start, end) }

fun newSchedule(name: String = "我的课表") = Schedule(name = name,
    semesterStartDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString())
