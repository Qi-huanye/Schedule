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
import kotlinx.coroutines.*
import java.time.LocalDateTime

open class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { WidgetUpdater.refresh(context) } finally { result.finish() } }
    }
}
class NextWidget : TodayWidget()

object WidgetUpdater {
    suspend fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val data = (context.applicationContext as PureApp).repository.snapshot().let { all -> all.firstOrNull { it.schedule.current } ?: all.firstOrNull() }
        val now = LocalDateTime.now()
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        listOf(TodayWidget::class.java, NextWidget::class.java).forEach { type ->
            val next = type == NextWidget::class.java
            val content = if (data == null) "还没有课表" else if (next) {
                CourseFilter.next(data, now)?.let { "${it.course.name}\n${it.date}  ${it.start.toLocalTime()} - ${it.end.toLocalTime()}\n${it.classroom}" } ?: "本学期没有后续课程"
            } else CourseFilter.onDate(data, now.toLocalDate()).joinToString("\n\n") { "${it.start.toLocalTime()}  ${it.course.name}\n${it.classroom}" }.ifBlank { "今天没有课程" }
            val views = RemoteViews(context.packageName, R.layout.course_widget).apply {
                setTextViewText(R.id.widget_title, if (next) "下一节课" else "今日课程 · ${now.monthValue}/${now.dayOfMonth}")
                setTextViewText(R.id.widget_body, content)
                setOnClickPendingIntent(R.id.widget_root, intent)
            }
            manager.getAppWidgetIds(ComponentName(context, type)).forEach { manager.updateAppWidget(it, views) }
        }
    }
}
