package dev.wakeuppure.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import dev.wakeuppure.MainActivity
import dev.wakeuppure.PureApp
import dev.wakeuppure.domain.model.ScheduleData
import dev.wakeuppure.domain.usecase.CourseFilter
import java.time.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun refresh(context: Context, data: List<ScheduleData>) {
        val manager = WorkManager.getInstance(context)
        val prefs = context.getSharedPreferences("reminder-deliveries", 0)
        val now = LocalDateTime.now()
        val names = mutableSetOf<String>()
        data.filter { it.schedule.reminderMinutes != null }.forEach { schedule ->
            for (day in 0..8) CourseFilter.onDate(schedule, now.toLocalDate().plusDays(day.toLong())).forEach occurrenceLoop@ { occurrence ->
                if (occurrence.start <= now) return@occurrenceLoop
                val key = "course-${schedule.schedule.id}-${occurrence.period.id}-${occurrence.start}-${schedule.schedule.reminderMinutes}"
                if (prefs.contains(key)) return@occurrenceLoop
                names.add(key)
                val due = occurrence.start.minusMinutes(schedule.schedule.reminderMinutes!!.toLong())
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(Duration.between(now, maxOf(due, now)).toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("key" to key, "schedule" to schedule.schedule.id,
                        "period" to occurrence.period.id, "start" to occurrence.start.toString(), "lead" to schedule.schedule.reminderMinutes))
                    .build()
                manager.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, request)
            }
        }
        TodoReminders.pending(data, Instant.now()).forEach { reminder ->
            if (prefs.contains(reminder.key)) return@forEach
            names.add(reminder.key)
            manager.enqueueUniqueWork(reminder.key, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TodoReminderWorker>()
                    .setInitialDelay((reminder.notifyAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("key" to reminder.key, "schedule" to reminder.scheduleId,
                        "todo" to reminder.todoId, "due" to reminder.dueAt, "lead" to reminder.lead))
                    .build())
        }
        val old = prefs.getStringSet("scheduled", emptySet()).orEmpty()
        (old - names).forEach { manager.cancelUniqueWork(it) }
        prefs.edit().putStringSet("scheduled", names).apply()
        if (data.any { it.schedule.reminderMinutes != null || it.schedule.todoReminderMinutes != null }) manager.enqueueUniquePeriodicWork(
            "refresh-course-reminders", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderRefreshWorker>(12, TimeUnit.HOURS).build())
        else manager.cancelUniqueWork("refresh-course-reminders")
    }
}

class ReminderRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ReminderScheduler.refresh(applicationContext, (applicationContext as PureApp).repository.snapshot())
        return Result.success()
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val schedule = (applicationContext as PureApp).repository.snapshot().find { it.schedule.id == inputData.getLong("schedule", -1) }
            ?: return Result.success()
        val lead = schedule.schedule.reminderMinutes ?: return Result.success()
        if (lead != inputData.getInt("lead", -1)) return Result.success()
        val start = runCatching { LocalDateTime.parse(inputData.getString("start")) }.getOrNull() ?: return Result.success()
        val key = inputData.getString("key") ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("reminder-deliveries", 0)
        if (prefs.contains(key)) return Result.success()
        val occurrence = CourseFilter.onDate(schedule, start.toLocalDate()).find { it.period.id == inputData.getLong("period", -1) && it.start == start }
            ?: return Result.success()
        val now = LocalDateTime.now()
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (now < occurrence.end && allowed) {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("courses", "课程提醒", NotificationManager.IMPORTANCE_DEFAULT))
            val intent = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(key, 1, NotificationCompat.Builder(applicationContext, "courses")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(occurrence.course.name)
                .setContentText(listOf(occurrence.start.toLocalTime().toString(), occurrence.classroom).filter { it.isNotBlank() }.joinToString("，"))
                .setContentIntent(intent).setAutoCancel(true).build())
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        }
        return Result.success()
    }
}


class TodoReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val data = (applicationContext as PureApp).repository.snapshot()
            .find { it.schedule.id == inputData.getLong("schedule", -1) } ?: return Result.success()
        val todo = data.todos.find { it.id == inputData.getLong("todo", -1) } ?: return Result.success()
        val lead = data.schedule.todoReminderMinutes ?: return Result.success()
        if (todo.completed || todo.dueAt != inputData.getLong("due", -1) || lead != inputData.getInt("lead", -1)) return Result.success()
        val key = inputData.getString("key") ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("reminder-deliveries", 0)
        if (prefs.contains(key)) return Result.success()
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return Result.success()
        // A delayed background job still explains that the deadline has passed.
        val now = Instant.now()
        if (now.toEpochMilli() < todo.dueAt - lead * 60_000L) return Result.success()
        val due = Instant.ofEpochMilli(todo.dueAt).atZone(ZoneId.systemDefault())
        val overdue = now.toEpochMilli() >= todo.dueAt
        val course = data.courses.firstOrNull { it.course.id == todo.courseId }?.course?.name
        val text = listOfNotNull(if (overdue) "已逾期，尚未完成" else "${due.toLocalTime()} 截止", course).joinToString("，")
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("todos", "待办提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = PendingIntent.getActivity(applicationContext, todo.id.hashCode(),
            Intent(applicationContext, MainActivity::class.java).putExtra("open_today", true)
                .putExtra("todo_id", todo.id).putExtra("schedule_id", data.schedule.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(key, 2, NotificationCompat.Builder(applicationContext, "todos")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(todo.title)
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent).setAutoCancel(true).build())
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        return Result.success()
    }
}
