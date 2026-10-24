package dev.wakeuppure

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.wakeuppure.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Runs against an isolated test schedule and removes it afterward. */
class ExpressiveWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var scheduleId = 0L
    private val name = "Expressive 界面流程测试"
    private val repository get() = (compose.activity.application as PureApp).repository

    @Before fun setUp() = runBlocking {
        val today = LocalDate.now()
        scheduleId = repository.saveSchedule(Schedule(name = name, semesterStartDate = today.toString()), defaultTimeSlots())
        repository.saveCourse(Course(scheduleId = scheduleId, name = "已有课程", classroom = "教学楼 201"),
            listOf(CoursePeriod(dayOfWeek = today.dayOfWeek.value)))
        repository.selectSchedule(scheduleId)
    }

    @After fun tearDown() = runBlocking {
        if (scheduleId != 0L) repository.deleteSchedule(scheduleId)
    }

    @Test fun navigateSettingsCreateCourseAndChangeWeeks() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("今日").performClick()
        compose.onNodeWithText("今日安排").assertIsDisplayed()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("关于 Schedule").performScrollTo().performClick()
        compose.onNodeWithText("GitHub 源代码").assertIsDisplayed()
        compose.onNodeWithText("开源许可").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("查找开源组件").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("隐私声明").performClick()
        compose.onNodeWithText("你的课程，留在你的设备上。").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("作息时间").performScrollTo().performClick()
        compose.onNodeWithText("每日时间表").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("导入与导出").performScrollTo().performClick()
        compose.onNodeWithText("导出当前课表").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("课表").performClick()
        compose.onNodeWithContentDescription("添加课程").performClick()
        compose.onNodeWithText("课程名").performTextInput("新增课程测试")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("新增课程测试").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("下一周").performClick()
        compose.onNodeWithText("第2周").assertIsDisplayed()
        compose.onNodeWithContentDescription("上一周").performClick()
        compose.onNodeWithText("第1周").assertIsDisplayed()
    }
}
