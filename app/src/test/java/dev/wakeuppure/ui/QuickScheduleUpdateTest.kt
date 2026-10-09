package dev.wakeuppure.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wakeuppure.data.local.PureDatabase
import dev.wakeuppure.data.repository.ScheduleRepository
import dev.wakeuppure.domain.model.Schedule
import dev.wakeuppure.domain.model.defaultTimeSlots
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuickScheduleUpdateTest {
    private lateinit var db: PureDatabase
    private lateinit var repository: ScheduleRepository
    private lateinit var vm: PureViewModel
    private var id = 0L

    @Before fun setUp(): Unit = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, PureDatabase::class.java).build()
        repository = ScheduleRepository(db)
        id = repository.saveSchedule(Schedule(name = "秋季", semesterStartDate = "2026-09-07", showWeekend = true, firstDay = 1), defaultTimeSlots())
        vm = PureViewModel(app, repository)
        withTimeout(5_000) { vm.schedules.first { it.isNotEmpty() } }
    }

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
        db.close()
    }

    @Test fun rapidChangesNeitherFlashNorOverwriteEachOther() = runBlocking {
        val weekend = mutableListOf<Boolean>()
        val firstDay = mutableListOf<Int>()
        val busy = mutableListOf<Boolean>()
        val watchers = listOf(
            launch(Dispatchers.Unconfined) { vm.schedules.mapNotNull { it.firstOrNull()?.schedule?.showWeekend }.distinctUntilChanged().collect { weekend += it } },
            launch(Dispatchers.Unconfined) { vm.schedules.mapNotNull { it.firstOrNull()?.schedule?.firstDay }.distinctUntilChanged().collect { firstDay += it } },
            launch(Dispatchers.Unconfined) { vm.busy.collect { busy += it } },
        )
        vm.updateSchedule(id) { it.copy(showWeekend = false) }
        vm.updateSchedule(id) { it.copy(firstDay = 7) }
        // Both changes show at once, before anything is written.
        assertFalse(vm.schedules.value.single().schedule.showWeekend)
        assertEquals(7, vm.schedules.value.single().schedule.firstDay)
        settle()
        watchers.forEach { it.cancel() }
        val stored = repository.snapshot().single().schedule
        assertFalse(stored.showWeekend)
        assertEquals(7, stored.firstDay)
        // Each control changed exactly once: no flip back to the old value while saving.
        assertEquals(listOf(true, false), weekend)
        assertEquals(listOf(1, 7), firstDay)
        // A busy flash would disable the neighbouring controls for a moment.
        assertFalse(busy.any { it })
    }

    @Test fun togglingTheSameSettingQuicklyKeepsTheLastValue() = runBlocking {
        vm.updateSchedule(id) { it.copy(showWeekend = false) }
        vm.updateSchedule(id) { it.copy(showWeekend = true) }
        vm.updateSchedule(id) { it.copy(showWeekend = false) }
        assertFalse(vm.schedules.value.single().schedule.showWeekend)
        settle()
        assertFalse(repository.snapshot().single().schedule.showWeekend)
        assertFalse(vm.schedules.value.single().schedule.showWeekend)
    }

    private suspend fun settle() = withTimeout(10_000) { while (vm.hasPendingEdits()) delay(10) }
}
