package dev.wakeuppure

import android.app.Application
import dev.wakeuppure.data.local.PureDatabase
import dev.wakeuppure.data.repository.ScheduleRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import dev.wakeuppure.notification.ReminderScheduler
import dev.wakeuppure.widget.WidgetUpdater

class PureApp : Application() {
    val database by lazy { PureDatabase.create(this) }
    val repository by lazy { ScheduleRepository(database) }
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.schedules.collectLatest { data ->
                ReminderScheduler.refresh(this@PureApp, data)
                WidgetUpdater.refresh(this@PureApp)
            }
        }
    }
}
