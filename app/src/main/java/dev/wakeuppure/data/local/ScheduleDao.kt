package dev.wakeuppure.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Transaction @Query("SELECT * FROM schedules ORDER BY id")
    fun observeSchedules(): Flow<List<ScheduleRelation>>
    @Transaction @Query("SELECT * FROM schedules ORDER BY id")
    suspend fun snapshot(): List<ScheduleRelation>
    @Query("SELECT * FROM schedules WHERE id = :id") suspend fun schedule(id: Long): ScheduleEntity?
    @Query("SELECT * FROM courses WHERE scheduleId = :id ORDER BY id") suspend fun scheduleCourses(id: Long): List<CourseEntity>
    @Query("SELECT * FROM courses WHERE id = :id") suspend fun course(id: Long): CourseEntity?
    @Query("SELECT * FROM course_periods WHERE id = :id") suspend fun period(id: Long): CoursePeriodEntity?
    @Query("SELECT * FROM time_slots WHERE scheduleId = :id ORDER BY section") suspend fun slots(id: Long): List<TimeSlotEntity>
    @Query("SELECT p.* FROM course_periods p JOIN courses c ON c.id = p.courseId WHERE c.scheduleId = :id")
    suspend fun schedulePeriods(id: Long): List<CoursePeriodEntity>
    @Insert suspend fun insertSchedule(value: ScheduleEntity): Long
    @Update suspend fun updateSchedule(value: ScheduleEntity)
    @Insert suspend fun insertCourse(value: CourseEntity): Long
    @Update suspend fun updateCourse(value: CourseEntity)
    @Update suspend fun updatePeriods(values: List<CoursePeriodEntity>)
    @Insert suspend fun insertPeriods(values: List<CoursePeriodEntity>)
    @Insert suspend fun insertSlots(values: List<TimeSlotEntity>)
    @Query("UPDATE schedules SET current = 0") suspend fun clearCurrent()
    @Query("UPDATE schedules SET current = 1 WHERE id = :id") suspend fun markCurrent(id: Long)
    @Query("DELETE FROM schedules WHERE id = :id") suspend fun deleteSchedule(id: Long)
    @Query("DELETE FROM courses WHERE id = :id") suspend fun deleteCourse(id: Long)
    @Query("DELETE FROM course_periods WHERE courseId = :id") suspend fun deletePeriods(id: Long)
    @Query("DELETE FROM time_slots WHERE scheduleId = :id") suspend fun deleteSlots(id: Long)
}
