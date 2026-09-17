package dev.wakeuppure.data.local

import androidx.room.*

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String, val semesterStartDate: String, val maxWeeks: Int,
    val current: Boolean, val createdAt: Long, val updatedAt: Long,
    val showWeekend: Boolean, val firstDay: Int, val reminderMinutes: Int?,
    val fixedLessonMinutes: Int? = null,
    @ColumnInfo(defaultValue = "'custom'") val colorPalette: String = "custom"
)

@Entity(tableName = "courses", foreignKeys = [ForeignKey(entity = ScheduleEntity::class,
    parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("scheduleId")])
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long, val scheduleId: Long,
    val name: String, val teacher: String, val classroom: String, val color: String, val note: String
)

@Entity(tableName = "course_periods", foreignKeys = [ForeignKey(entity = CourseEntity::class,
    parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("courseId")])
data class CoursePeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long, val courseId: Long,
    val dayOfWeek: Int, val startSection: Int, val endSection: Int,
    val startWeek: Int, val endWeek: Int, val weekType: String, val classroom: String
)

@Entity(tableName = "time_slots", primaryKeys = ["scheduleId", "section"],
    foreignKeys = [ForeignKey(entity = ScheduleEntity::class, parentColumns = ["id"],
        childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("scheduleId")])
data class TimeSlotEntity(val scheduleId: Long, val section: Int, val startTime: String, val endTime: String)

data class CourseRelation(
    @Embedded val course: CourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId") val periods: List<CoursePeriodEntity>
)

data class ScheduleRelation(
    @Embedded val schedule: ScheduleEntity,
    @Relation(entity = CourseEntity::class, parentColumn = "id", entityColumn = "scheduleId")
    val courses: List<CourseRelation>,
    @Relation(parentColumn = "id", entityColumn = "scheduleId") val slots: List<TimeSlotEntity>
)
