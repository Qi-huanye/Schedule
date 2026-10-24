package dev.wakeuppure.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.wakeuppure.MainActivity
import dev.wakeuppure.PureApp
import dev.wakeuppure.R
import dev.wakeuppure.domain.usecase.CourseFilter
import dev.wakeuppure.domain.usecase.TodoSchedule
import dev.wakeuppure.domain.usecase.Occurrence
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.LocalTime

/** Compact widget: the current class, or the next class scheduled today. */
open class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { WidgetUpdater.refresh(context) } finally { result.finish() } }
    }
}

/** Expanded widget: the remaining schedule for today. */
class NextWidget : TodayWidget()

/** Small home screen widget for deadlines due today. */
class TodoWidget : TodayWidget()

object WidgetUpdater {
    private const val MAX_EXPANDED_ROWS = 3

    suspend fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val compactIds = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
        val expandedIds = manager.getAppWidgetIds(ComponentName(context, NextWidget::class.java))
        val todoIds = manager.getAppWidgetIds(ComponentName(context, TodoWidget::class.java))
        if (compactIds.isEmpty() && expandedIds.isEmpty() && todoIds.isEmpty()) return

        val data = (context.applicationContext as PureApp).repository.snapshot()
            .let { all -> all.firstOrNull { it.schedule.current } ?: all.firstOrNull() }
        val now = LocalDateTime.now()
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        if (compactIds.isNotEmpty()) compactIds.forEach { id ->
            manager.updateAppWidget(id, compactViews(context, data?.let { CourseFilter.onDate(it, now.toLocalDate()) }, now, open))
        }
        if (expandedIds.isNotEmpty()) expandedIds.forEach { id ->
            manager.updateAppWidget(id, expandedViews(context, data?.let { CourseFilter.onDate(it, now.toLocalDate()) } ?: emptyList(), now, open))
        }
        if (todoIds.isNotEmpty()) todoIds.forEach { id ->
            manager.updateAppWidget(id, todoViews(context, TodoSchedule.agenda(data?.todos.orEmpty(), now.toLocalDate()),
                PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).putExtra("open_today", true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)))
        }
    }

    private fun compactViews(context: Context, today: List<Occurrence>?, now: LocalDateTime, open: PendingIntent): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.course_widget_compact)
        val active = today?.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
        val next = today?.firstOrNull { it.start.isAfter(now) }
        val item = active ?: next
        views.setTextViewText(R.id.widget_title, if (active != null) "正在上课" else "下一节课")
        views.setTextViewText(R.id.widget_course, item?.course?.name ?: "今日无课")
        views.setTextViewText(R.id.widget_meta, item?.let { occurrenceMeta(it) } ?: "今天没有安排课程")
        views.setInt(R.id.widget_accent, "setBackgroundResource", if (active != null) R.drawable.widget_current_pill else R.drawable.widget_accent_pill)
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }

    private fun expandedViews(context: Context, today: List<Occurrence>, now: LocalDateTime, open: PendingIntent): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.course_widget_expanded)
        val active = today.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
        val ordered = if (active == null) today.filter { it.end.isAfter(now) } else listOf(active) + today.filter { it != active && it.end.isAfter(now) }
        val rows = ordered.take(MAX_EXPANDED_ROWS)
        views.setTextViewText(R.id.widget_title, "今日课表")
        views.setTextViewText(R.id.widget_date, "${now.monthValue}月${now.dayOfMonth}日")
        views.setOnClickPendingIntent(R.id.widget_root, open)
        val rowIds = listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)
        val timeIds = listOf(R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3)
        val courseIds = listOf(R.id.widget_course_1, R.id.widget_course_2, R.id.widget_course_3)
        val metaIds = listOf(R.id.widget_meta_1, R.id.widget_meta_2, R.id.widget_meta_3)
        rows.forEachIndexed { index, item ->
            views.setViewVisibility(rowIds[index], android.view.View.VISIBLE)
            views.setTextViewText(timeIds[index], "${item.start.toLocalTime().short()}–${item.end.toLocalTime().short()}")
            views.setTextViewText(courseIds[index], item.course.name)
            views.setTextViewText(metaIds[index], occurrenceMeta(item))
            views.setInt(rowIds[index], "setBackgroundResource", if (item == active) R.drawable.widget_current_row else R.drawable.widget_course_row)
        }
        for (index in rows.size until rowIds.size) views.setViewVisibility(rowIds[index], android.view.View.GONE)
        views.setViewVisibility(R.id.widget_empty, if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
        views.setTextViewText(R.id.widget_empty, if (today.isEmpty()) "今天没有课程" else "今天的课程已结束")
        return views
    }

    private fun todoViews(context: Context, todos: List<dev.wakeuppure.domain.model.Todo>, open: PendingIntent): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.todo_widget)
        val pending = todos.firstOrNull { !it.completed } ?: todos.firstOrNull()
        val overdue = pending != null && !pending.completed && pending.dueAt < System.currentTimeMillis()
        views.setTextViewText(R.id.todo_widget_title, if (pending == null) "今日待办" else if (pending.completed) "待办已完成" else if (overdue) "逾期待办" else "今日待办")
        views.setTextViewText(R.id.todo_widget_task, pending?.title ?: "今天没有待办")
        views.setTextViewText(R.id.todo_widget_meta, pending?.let {
            val time = java.time.Instant.ofEpochMilli(it.dueAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern(if (overdue) "M/d HH:mm" else "HH:mm"))
            (if (overdue) "$time 已逾期" else "$time 截止") + if (todos.count { item -> !item.completed } > 1) "，另有 ${todos.count { item -> !item.completed } - 1} 项" else ""
        } ?: "轻松安排一天")
        views.setOnClickPendingIntent(R.id.todo_widget_root, open)
        return views
    }

    private fun occurrenceMeta(item: Occurrence): String = listOf(item.teacher, item.classroom)
        .filter { it.isNotBlank() }.joinToString("  /  ").ifBlank { "${item.course.name}的课程安排" }

    private fun LocalTime.short(): String = toString().substring(0, 5)
}
