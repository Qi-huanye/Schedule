package dev.wakeuppure.data.repository

import androidx.room.withTransaction
import dev.wakeuppure.data.local.*
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.SemesterAdjustment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

class ScheduleRepository(private val db: PureDatabase) {
    private val dao = db.scheduleDao()
    val schedules: Flow<List<ScheduleData>> = dao.observeSchedules().map { rows -> rows.map { it.model() } }

    suspend fun snapshot(): List<ScheduleData> = dao.snapshot().map { it.model() }

    suspend fun saveSchedule(schedule: Schedule, slots: List<TimeSlot>, preserveDates: Boolean = false, weekOffset: Int = 0): Long = db.withTransaction {
        val previous = if (schedule.id == 0L) null else requireNotNull(dao.schedule(schedule.id)) { "Schedule does not exist" }
        val original = dao.schedulePeriods(schedule.id).map { it.model() }
        val adjusted = SemesterAdjustment.adjust(original, previous?.semesterStartDate ?: schedule.semesterStartDate,
            schedule.semesterStartDate, preserveDates, weekOffset)
        val adjustedSchedule = if (adjusted != original) schedule.copy(maxWeeks = maxOf(schedule.maxWeeks, adjusted.maxOfOrNull { it.endWeek } ?: 1)) else schedule
        validateSchedule(adjustedSchedule, slots)
        adjusted.forEach { validatePeriod(it, adjustedSchedule.maxWeeks, slots) }
        if (adjusted != original) dao.updatePeriods(adjusted.map { it.entity() })
        val current = schedule.current || previous?.current == true || dao.snapshot().isEmpty()
        if (current) dao.clearCurrent()
        val value = adjustedSchedule.copy(current = current)
        val id = if (previous == null) dao.insertSchedule(value.entity()) else {
            dao.updateSchedule(value.entity())
            schedule.id
        }
        if (value.colorPalette != "custom" && value.colorPalette != previous?.colorPalette) {
            dao.scheduleCourses(id).forEachIndexed { index, course ->
                dao.updateCourse(course.copy(color = CoursePalettes.color(value.colorPalette, index)))
            }
        }
        dao.deleteSlots(id)
        dao.insertSlots(slots.map { it.entity(id) })
        id
    }

    suspend fun selectSchedule(id: Long): Unit = db.withTransaction {
        requireNotNull(dao.schedule(id)) { "Schedule does not exist" }
        dao.clearCurrent()
        dao.markCurrent(id)
    }

    suspend fun deleteSchedule(id: Long): Unit = db.withTransaction {
        dao.deleteSchedule(id)
        ensureCurrent()
    }

    suspend fun saveCourse(course: Course, periods: List<CoursePeriod>, timeSlots: List<TimeSlot>? = null): Long = db.withTransaction {
        val schedule = requireNotNull(dao.schedule(course.scheduleId)) { "Schedule does not exist" }
        if (timeSlots != null) saveSchedule(schedule.model(), timeSlots)
        validateCourse(course)
        require(periods.isNotEmpty() && periods.size <= 1000) { "Course must contain 1 to 1000 periods" }
        val previous = if (course.id == 0L) null else requireNotNull(dao.course(course.id)) { "Course does not exist" }
        require(previous == null || previous.scheduleId == course.scheduleId) { "Course belongs to a different schedule" }
        val slots = dao.slots(course.scheduleId).map { it.model() }
        periods.forEach { period ->
            require(period.courseId == 0L || (course.id != 0L && period.courseId == course.id)) { "Period belongs to another course" }
            if (period.id != 0L) {
                val stored = requireNotNull(dao.period(period.id)) { "Period does not exist" }
                require(course.id != 0L && stored.courseId == course.id) { "Period belongs to another course" }
            }
            validatePeriod(period, schedule.maxWeeks, slots)
        }
        val id = if (previous == null) dao.insertCourse(course.entity()) else {
            dao.updateCourse(course.entity())
            course.id
        }
        dao.deletePeriods(id)
        dao.insertPeriods(normalizePeriods(periods, id).map { it.entity() })
        id
    }

    suspend fun deleteCourse(id: Long): Unit = db.withTransaction { dao.deleteCourse(id) }

    suspend fun importSchedules(data: List<ScheduleData>): List<Long> = db.withTransaction {
        require(data.size <= 100) { "Too many schedules" }
        val result = data.map { incoming ->
            validateSchedule(incoming.schedule, incoming.timeSlots)
            require(incoming.courses.size <= 5000) { "Too many courses" }
            val id = dao.insertSchedule(incoming.schedule.copy(id = 0, current = false).entity())
            dao.insertSlots(incoming.timeSlots.map { it.entity(id) })
            incoming.courses.forEach { item ->
                validateCourse(item.course)
                require(item.course.scheduleId == 0L || item.course.scheduleId == incoming.schedule.id) { "Course belongs to another schedule" }
                require(item.periods.isNotEmpty() && item.periods.size <= 1000) { "Course must contain 1 to 1000 periods" }
                item.periods.forEach { period ->
                    require(period.courseId == 0L || period.courseId == item.course.id) { "Period belongs to another course" }
                    validatePeriod(period, incoming.schedule.maxWeeks, incoming.timeSlots)
                }
                val courseId = dao.insertCourse(item.course.copy(id = 0, scheduleId = id).entity())
                dao.insertPeriods(normalizePeriods(item.periods, courseId).map { it.entity() })
            }
            if (incoming.schedule.current) {
                dao.clearCurrent()
                dao.markCurrent(id)
            }
            id
        }
        ensureCurrent()
        result
    }

    private suspend fun ensureCurrent() {
        val rows = dao.snapshot()
        if (rows.none { it.schedule.current }) rows.firstOrNull()?.let { dao.markCurrent(it.schedule.id) }
    }

    private fun normalizePeriods(periods: List<CoursePeriod>, courseId: Long) = periods
        .map { it.copy(id = 0, courseId = courseId) }.distinct()

    private fun validateSchedule(schedule: Schedule, slots: List<TimeSlot>) {
        require(schedule.fixedLessonMinutes == null || schedule.fixedLessonMinutes in 1..240)
        require(schedule.colorPalette == "custom" || CoursePalettes.find(schedule.colorPalette) != null)
        schedule.fixedLessonMinutes?.let { minutes ->
            require(slots.all { dev.wakeuppure.domain.usecase.FixedLessonTime.end(it.startTime, minutes) == it.endTime })
        }
        require(schedule.id >= 0) { "Invalid schedule ID" }
        require(schedule.name.isNotBlank() && schedule.name.length <= 200) { "Schedule name must contain 1 to 200 characters" }
        require(schedule.semesterStartDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Invalid semester start date" }
        require(runCatching { LocalDate.parse(schedule.semesterStartDate) }.isSuccess) { "Invalid semester start date" }
        require(schedule.maxWeeks in 1..60) { "Maximum weeks must be between 1 and 60" }
        require(schedule.firstDay in 1..7) { "Invalid first day" }
        require(schedule.reminderMinutes == null || schedule.reminderMinutes in 0..1440) { "Invalid reminder minutes" }
        require(slots.isNotEmpty() && slots.size <= 30) { "Timetable must contain 1 to 30 sections" }
        val ordered = slots.sortedBy { it.section }
        require(ordered.map { it.section } == (1..ordered.size).toList()) { "Sections must be unique and consecutive" }
        var previousEnd: LocalTime? = null
        ordered.forEach { slot ->
            require(slot.startTime.matches(Regex("\\d{2}:\\d{2}")) && slot.endTime.matches(Regex("\\d{2}:\\d{2}"))) { "Invalid section time" }
            val start = runCatching { LocalTime.parse(slot.startTime) }.getOrNull()
            val end = runCatching { LocalTime.parse(slot.endTime) }.getOrNull()
            require(start != null && end != null && start < end) { "Section must end after it starts" }
            previousEnd?.let { require(start >= it) { "Sections must not overlap" } }
            previousEnd = end
        }
    }

    private fun validateCourse(course: Course) {
        require(course.id >= 0 && course.scheduleId >= 0) { "Invalid course ID" }
        require(course.name.isNotBlank() && course.name.length <= 200) { "Course name must contain 1 to 200 characters" }
        require(course.teacher.length <= 200 && course.classroom.length <= 200 && course.note.length <= 10000) { "Course text is too long" }
        require(course.color.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) { "Invalid course color" }
    }

    private fun validatePeriod(period: CoursePeriod, maxWeeks: Int, slots: List<TimeSlot>) {
        require(period.id >= 0 && period.courseId >= 0) { "Invalid period ID" }
        require(period.dayOfWeek in 1..7) { "Invalid weekday" }
        require(period.startWeek in 1..maxWeeks && period.endWeek in period.startWeek..maxWeeks) { "Invalid week range" }
        require(period.startSection >= 1 && period.endSection >= period.startSection &&
            (period.startSection..period.endSection).all { section -> slots.any { it.section == section } }) { "Period references missing sections" }
        require(period.classroom.length <= 200) { "Classroom text is too long" }
    }
}
