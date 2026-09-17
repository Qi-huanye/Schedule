package dev.wakeuppure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wakeuppure.data.wakeup.json.WakeUpJsonImporter
import dev.wakeuppure.data.export.ScheduleExporter
import dev.wakeuppure.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

@RunWith(AndroidJUnit4::class)
class DeviceWorkflowTest {
    @Test fun bothWidgetsBindAndRender() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PureApp
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        val userId = android.os.Process.myUserHandle().hashCode()
        shell("appwidget grantbind --package dev.wakeuppure --user $userId")
        lateinit var host: android.appwidget.AppWidgetHost
        var hostCreated = false
        val views = mutableListOf<android.appwidget.AppWidgetHostView>()
        try {
            instrumentation.runOnMainSync {
                host = android.appwidget.AppWidgetHost(app, 701)
                hostCreated = true
                val manager = android.appwidget.AppWidgetManager.getInstance(app)
                listOf(dev.wakeuppure.widget.TodayWidget::class.java, dev.wakeuppure.widget.NextWidget::class.java).forEach { type ->
                    val id = host.allocateAppWidgetId()
                    assertTrue(manager.bindAppWidgetIdIfAllowed(id, android.content.ComponentName(app, type)))
                    views += host.createView(app, id, manager.getAppWidgetInfo(id))
                }
                host.startListening()
            }
            dev.wakeuppure.widget.WidgetUpdater.refresh(app)
            withTimeout(10_000) {
                var ready = false
                while (!ready) {
                    instrumentation.runOnMainSync {
                        ready = views.all { !it.findViewById<android.widget.TextView>(R.id.widget_body)?.text.isNullOrBlank() }
                    }
                    if (!ready) delay(100)
                }
            }
            instrumentation.runOnMainSync {
                assertTrue(views[0].findViewById<android.widget.TextView>(R.id.widget_title).text.startsWith("今日课程"))
                assertEquals("下一节课", views[1].findViewById<android.widget.TextView>(R.id.widget_title).text.toString())
            }
        } finally {
            instrumentation.runOnMainSync { if (hostCreated) { host.stopListening(); host.deleteHost() } }
            shell("appwidget revokebind --package dev.wakeuppure --user $userId")
        }
    }

    @Test fun concurrentCoursesProduceTwoNotifications() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PureApp
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.executeShellCommand(
            "pm grant dev.wakeuppure android.permission.POST_NOTIFICATIONS").close()
        val now = LocalDateTime.now()
        if (now.hour == 23) return@runBlocking
        val start = now.plusMinutes(2).withSecond(0).withNano(0)
        val end = start.plusMinutes(10)
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val id = app.repository.saveSchedule(Schedule(name = "Reminder test", semesterStartDate = now.toLocalDate().with(java.time.DayOfWeek.MONDAY).toString(), reminderMinutes = 5),
            listOf(TimeSlot(1, start.format(fmt), end.format(fmt))))
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        try {
            listOf("Concurrent A", "Concurrent B").forEach { name ->
                app.repository.saveCourse(Course(scheduleId = id, name = name), listOf(CoursePeriod(dayOfWeek = now.dayOfWeek.value, startSection = 1, endSection = 1)))
            }
            withTimeout(30_000) {
                while (manager.activeNotifications.count { it.notification.extras.getString("android.title")?.startsWith("Concurrent") == true } < 2) delay(500)
            }
            assertEquals(2, manager.activeNotifications.count { it.notification.extras.getString("android.title")?.startsWith("Concurrent") == true })
        } finally {
            app.repository.deleteSchedule(id)
            manager.activeNotifications.filter { it.notification.extras.getString("android.title")?.startsWith("Concurrent") == true }.forEach { manager.cancel(it.tag, it.id) }
        }
    }

    @Test fun repositoryImportEditExportOnDevice() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PureApp
        val repo = app.repository
        val start = LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString()
        val fixture = """{"name":"2026 秋季 · 演示课表","startDate":"$start","courses":[
          {"name":"高等数学","teacher":"张老师","position":"教学楼 201","day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"color":"#C1E8DE"},
          {"name":"大学英语","teacher":"李老师","position":"文科楼 302","day":2,"startNode":3,"step":2,"startWeek":1,"endWeek":16,"color":"#BFDDF1"},
          {"name":"计算机网络","teacher":"王老师","position":"三号楼 302","day":3,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"color":"#E4D5F3"},
          {"name":"数据结构","position":"实验楼 205","day":3,"startNode":5,"step":2,"startWeek":1,"endWeek":16,"color":"#F4E3B5"},
          {"name":"线性代数","position":"教学楼 203","day":4,"startNode":3,"step":2,"startWeek":1,"endWeek":16,"type":1,"color":"#F3D3D2"},
          {"name":"体育","position":"运动场","day":5,"startNode":5,"step":2,"startWeek":1,"endWeek":16,"color":"#CFE4BD"},
          {"name":"实验课","position":"机房 301","day":7,"startNode":3,"step":2,"startWeek":1,"endWeek":16,"color":"#C1E8DE"}
        ]}"""
        val imported = WakeUpJsonImporter.parse(fixture)
        val id = repo.importSchedules(listOf(imported)).single()
        try {
            repo.selectSchedule(id)
            val data = repo.snapshot().single { it.schedule.id == id }
            assertEquals(7, data.courses.size)
            val original = data.courses.first()
            repo.saveCourse(original.course.copy(note = "已编辑"), original.periods)
            val edited = repo.snapshot().single { it.schedule.id == id }
            assertEquals("已编辑", edited.courses.first().course.note)
            assertEquals(7, WakeUpJsonImporter.parse(ScheduleExporter.json(edited)).courses.size)
            assertTrue(ScheduleExporter.ics(edited).contains("BEGIN:VEVENT"))
            assertEquals(edited, ScheduleExporter.restore(ScheduleExporter.backup(listOf(edited))).schedules.single())
        } finally {
            if (InstrumentationRegistry.getArguments().getString("keepDemo") != "true") repo.deleteSchedule(id)
        }
    }
}
