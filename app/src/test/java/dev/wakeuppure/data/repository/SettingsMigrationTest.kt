package dev.wakeuppure.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.wakeuppure.data.local.PureDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=android.app.Application::class)
class SettingsMigrationTest {
    @Test fun upgradesRealV1SchemaWithoutLosingSchedule() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("wakeup-pure.db")
        context.deleteDatabase(file.name)
        file.parentFile!!.mkdirs()
        val schemaFile = listOf(File("schemas/dev.wakeuppure.data.local.PureDatabase/1.json"), File("app/schemas/dev.wakeuppure.data.local.PureDatabase/1.json")).first { it.exists() }
        val database = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
            database.getValue("entities").jsonArray.forEach { entity ->
                val row=entity.jsonObject
                val table=row.getValue("tableName").jsonPrimitive.content
                db.execSQL(row.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))
                row["indices"]?.jsonArray?.forEach { index -> db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table)) }
            }
            database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO schedules VALUES (1,'Migration test','2026-08-31',20,1,0,0,1,1,NULL)")
            db.version=1
        }
        val room=PureDatabase.create(context)
        try {
            val restored=ScheduleRepository(room).snapshot().single().schedule
            assertEquals("Migration test",restored.name)
            assertEquals("2026-08-31",restored.semesterStartDate)
            assertNull(restored.fixedLessonMinutes)
            assertEquals("custom",restored.colorPalette)
        } finally { room.close(); context.deleteDatabase(file.name) }
    }
}
