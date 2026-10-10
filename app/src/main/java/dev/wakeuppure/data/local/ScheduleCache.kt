package dev.wakeuppure.data.local

import android.content.Context
import dev.wakeuppure.data.export.ScheduleExporter
import dev.wakeuppure.domain.model.ScheduleData

/**
 * Last schedules the database reported, kept in SharedPreferences so a cold start can draw the
 * timetable before Room's first query returns instead of an empty-state page. Speculative writes
 * are never cached: the snapshot only follows what the database actually holds.
 */
class ScheduleCache(context: Context) {
    private val prefs = context.getSharedPreferences("scheduleCache", 0)

    fun load(): List<ScheduleData> {
        val text = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { ScheduleExporter.restore(text).schedules }.getOrDefault(emptyList())
    }

    fun save(data: List<ScheduleData>) {
        val text = runCatching { ScheduleExporter.backup(data) }.getOrNull() ?: return
        if (text == prefs.getString(KEY, null)) return
        prefs.edit().putString(KEY, text).apply()
    }

    private companion object { const val KEY = "schedules" }
}
