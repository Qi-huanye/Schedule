package dev.wakeuppure.data.local

import dev.wakeuppure.domain.model.*

internal fun Schedule.entity() = ScheduleEntity(id, name, semesterStartDate, maxWeeks, current,
    createdAt, updatedAt, showWeekend, firstDay, reminderMinutes, fixedLessonMinutes, colorPalette)
internal fun ScheduleEntity.model() = Schedule(id, name, semesterStartDate, maxWeeks, current,
    createdAt, updatedAt, showWeekend, firstDay, reminderMinutes, fixedLessonMinutes, colorPalette)
internal fun Course.entity() = CourseEntity(id, scheduleId, name, teacher, classroom, color, note)
internal fun CourseEntity.model() = Course(id, scheduleId, name, teacher, classroom, color, note)
internal fun CoursePeriod.entity() = CoursePeriodEntity(id, courseId, dayOfWeek, startSection,
    endSection, startWeek, endWeek, weekType.name, classroom)
internal fun CoursePeriodEntity.model() = CoursePeriod(id, courseId, dayOfWeek, startSection,
    endSection, startWeek, endWeek, WeekType.valueOf(weekType), classroom)
internal fun TimeSlot.entity(scheduleId: Long) = TimeSlotEntity(scheduleId, section, startTime, endTime)
internal fun TimeSlotEntity.model() = TimeSlot(section, startTime, endTime)
internal fun ScheduleRelation.model() = ScheduleData(schedule.model(), courses.sortedBy { it.course.id }.map {
    CourseWithPeriods(it.course.model(), it.periods.sortedBy { period -> period.id }.map(CoursePeriodEntity::model))
}, slots.sortedBy { it.section }.map(TimeSlotEntity::model))
