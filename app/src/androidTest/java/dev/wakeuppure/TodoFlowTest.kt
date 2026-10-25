package dev.wakeuppure

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.PureTheme
import dev.wakeuppure.ui.todo.TodoEditorSheet
import dev.wakeuppure.ui.today.TodayScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class TodoFlowTest {
    @get:Rule val compose = createComposeRule()
    private val now = LocalDateTime.of(2026, 10, 24, 9, 0)
    private fun sample() = Todo(id = 3, scheduleId = 1, title = "提交报告", dueAt = now.plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

    @Test fun dateAndTimePickersOpenAndKeyboardTimeSaves() {
        var saved: Todo? = null
        compose.setContent { PureTheme("light") { TodoEditorSheet(1, emptyList(), now, {}, { saved = it }) } }
        compose.onNodeWithText("待办事项").performTextInput("提交报告")
        compose.onNodeWithContentDescription("截止日期，", substring = true).performClick()
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithContentDescription("截止时间，", substring = true).performClick()
        compose.onNodeWithText("待办截止时间").assertIsDisplayed()
        compose.onNodeWithContentDescription("切换为键盘输入").performClick()
        compose.onNode(hasSetTextAction() and hasText("10")).performTextReplacement("17")
        compose.onNode(hasSetTextAction() and hasText("00")).performTextReplacement("30")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithContentDescription("截止时间，17:30").assertExists()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(LocalTime.of(17,30), Instant.ofEpochMilli(saved!!.dueAt).atZone(ZoneId.systemDefault()).toLocalTime()) }
    }

    @Test fun courseMenuShowsSelectedStateAndSavesTheChosenCourse() {
        val course = CourseWithPeriods(Course(id = 42, name = "微积分"), emptyList())
        var saved: Todo? = null
        compose.setContent { PureTheme("light") {
            TodoEditorSheet(1, listOf(course), now, {}, { saved = it })
        } }
        compose.onNodeWithText("不关联课程").performClick()
        compose.onNodeWithText("微积分").assertIsDisplayed().performClick()
        compose.onNodeWithText("待办事项").performTextInput("复习积分")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(42L, saved!!.courseId) }
    }

    @Test fun existingTodoCanBeCompletedEditedAndDeletedWithConfirmation() {
        var saved: Todo? = null
        var deleted = false
        compose.setContent { PureTheme("light") { TodoEditorSheet(1, emptyList(), now, {}, { saved = it }, todo = sample(), onDelete = { deleted = true }) } }
        compose.onNodeWithText("待办事项").performTextReplacement("完成前修改报告")
        compose.onNodeWithText("标记完成").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(saved!!.completed); assertEquals("完成前修改报告", saved!!.title) }
        compose.onNodeWithText("待办事项").performTextReplacement("修改报告")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("修改报告", saved!!.title) }
        compose.onNodeWithText("删除待办").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }

    @Test fun unfinishedYesterdaysTodoAppearsInsideTodaysAgendaAndOpensDetails() {
        val todo = sample().copy(dueAt = now.minusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        var opened = false
        compose.setContent { PureTheme("light") { TodayScreen(ScheduleData(Schedule(id = 1, name = "课表", semesterStartDate = "2026-10-19"), emptyList(), todos = listOf(todo)), now, {}, {}, { opened = true }) } }
        compose.onNodeWithText("提交报告").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
        compose.onNodeWithText("已逾期", substring = true).assertExists()
        compose.onNodeWithText("待办", substring = false).assertDoesNotExist()
    }
}
