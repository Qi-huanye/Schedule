package dev.wakeuppure

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.wakeuppure.domain.model.Schedule
import dev.wakeuppure.domain.model.ScheduleData
import dev.wakeuppure.ui.PureTheme
import dev.wakeuppure.ui.settings.ExportScheduleDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExportSelectionTest {
    @get:Rule val compose = createComposeRule()
    private val schedules = (1L..3L).map {
        ScheduleData(Schedule(id = it, name = "课表 $it", semesterStartDate = "2026-10-19"), emptyList())
    }

    @Test fun defaultsToAllAndConfirmsMultipleSchedulesWithoutAllowingEmptySelection() {
        var confirmed: Set<Long>? = null
        compose.setContent { PureTheme("light") {
            ExportScheduleDialog(schedules, null, {}, { confirmed = it })
        } }
        compose.onNodeWithText("课表 1").assertIsOn()
        compose.onNodeWithText("课表 2").assertIsOn()
        compose.onNodeWithText("课表 3").assertIsOn()
        compose.onNodeWithText("全部课表").performClick()
        compose.onNodeWithText("确定").assertIsNotEnabled()
        compose.onNodeWithText("课表 1").performClick()
        compose.onNodeWithText("课表 3").performClick()
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertEquals(setOf(1L, 3L), confirmed) }
    }

    @Test fun cancelDiscardsDraftAndReopeningRestoresConfirmedSelection() {
        var visible by mutableStateOf(true)
        var confirmed: Set<Long>? = setOf(1L, 3L)
        compose.setContent { PureTheme("light") {
            if (visible) ExportScheduleDialog(schedules, confirmed, { visible = false }, { confirmed = it })
        } }
        compose.onNodeWithText("课表 2").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("课表 2").assertIsOff()
        compose.onNodeWithText("课表 1").assertIsOn()
        compose.onNodeWithText("课表 3").assertIsOn()
    }
}
