package dev.wakeuppure

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.PureTheme
import dev.wakeuppure.ui.components.*
import dev.wakeuppure.ui.today.TodayScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ExpressiveUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectedChoicesRemainMutuallyExclusive() {
        var selected by mutableStateOf("system")
        compose.setContent {
            PureTheme("light") {
                Segments(listOf("system" to "系统", "light" to "浅色", "dark" to "深色"), selected, { selected = it })
            }
        }
        compose.onNodeWithText("系统").assertIsOn()
        compose.onNodeWithText("浅色").performClick().assertIsOn()
        compose.onNodeWithText("系统").assertIsOff()
        compose.runOnIdle { assertEquals("light", selected) }
    }

    @Test fun narrowWeekdayGridWrapsInsteadOfShrinkingTouchTargets() {
        var picked = 1
        compose.setContent {
            PureTheme("dark") {
                Box(Modifier.width(280.dp)) {
                    SelectGrid((1..7).toList(), 7, { CellState.NONE }, { picked = it },
                        label = { WEEKDAYS[it - 1] }, describe = { "周${WEEKDAYS[it - 1]}" })
                }
            }
        }
        compose.onNodeWithContentDescription("周日").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(7, picked) }
    }

    @Test fun ongoingLessonTakesPriorityOverNextLesson() {
        val data = ScheduleData(Schedule(name = "测试", semesterStartDate = "2026-10-19"), courses = listOf(
            CourseWithPeriods(Course(id = 1, name = "高等数学", classroom = "教学楼 201"), listOf(CoursePeriod(id = 1))),
            CourseWithPeriods(Course(id = 2, name = "大学英语"), listOf(CoursePeriod(id = 2, startSection = 3, endSection = 4))),
        ))
        compose.setContent { PureTheme("light") { TodayScreen(data, LocalDateTime.of(2026, 10, 19, 8, 15)) {} } }
        compose.onNodeWithText("正在上课").assertIsDisplayed()
        compose.onNodeWithText("下一节课").assertDoesNotExist()
        compose.onAllNodesWithText("高等数学").onFirst().assertIsDisplayed()
    }
}
