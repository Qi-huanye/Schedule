package dev.wakeuppure.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScheduleEntity::class, CourseEntity::class, CoursePeriodEntity::class,
    TimeSlotEntity::class, TodoEntity::class], version = 4, exportSchema = true)
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
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN todoReminderMinutes INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, title TEXT NOT NULL, dueAt INTEGER NOT NULL, courseId INTEGER, completed INTEGER NOT NULL, FOREIGN KEY(scheduleId) REFERENCES schedules(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(courseId) REFERENCES courses(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_scheduleId ON todos(scheduleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_courseId ON todos(courseId)")
            }
        }
        fun create(context: Context): PureDatabase = Room.databaseBuilder(
            context.applicationContext, PureDatabase::class.java, "wakeup-pure.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
