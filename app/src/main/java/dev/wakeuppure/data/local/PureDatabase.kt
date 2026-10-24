package dev.wakeuppure.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScheduleEntity::class, CourseEntity::class, CoursePeriodEntity::class,
    TimeSlotEntity::class], version = 3, exportSchema = true)
abstract class PureDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN fixedLessonMinutes INTEGER")
                db.execSQL("ALTER TABLE schedules ADD COLUMN colorPalette TEXT NOT NULL DEFAULT 'custom'")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE course_periods ADD COLUMN teacher TEXT NOT NULL DEFAULT ''")
            }
        }
        fun create(context: Context): PureDatabase = Room.databaseBuilder(
            context.applicationContext, PureDatabase::class.java, "wakeup-pure.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
