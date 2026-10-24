package dev.wakeuppure

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wakeuppure.data.local.TimetableCache
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.PureTheme
import dev.wakeuppure.ui.background.imageColorScheme
import dev.wakeuppure.ui.settings.*
import dev.wakeuppure.ui.timetable.WeekPager
import dev.wakeuppure.ui.update.*
import dev.wakeuppure.data.update.AppRelease
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DesignRefinementTest {
    @get:Rule val compose = createComposeRule()

    @Test fun denseWeekSwipesSettleAtNarrowWidthAndLargeFont() {
        val now = LocalDateTime.of(2026, 10, 19, 8, 0)
        val data = ScheduleData(Schedule(name = "滑动回归", semesterStartDate = "2026-10-19"),
            (0 until 56).map { i -> CourseWithPeriods(Course(id = i + 1L, name = "课程 $i"),
                listOf(CoursePeriod(id = i + 1L, dayOfWeek = i % 7 + 1, startSection = (i / 7) % 8 + 1, endSection = ((i / 7) % 8 + 2).coerceAtMost(12)))) })
        val timetable = runBlocking { TimetableCache(ApplicationProvider.getApplicationContext()).prepare(data, now.toLocalDate()) }
        lateinit var pager: PagerState
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PureTheme("light") {
                    pager = rememberPagerState(initialPage = 0, pageCount = { 20 })
                    Box(Modifier.width(320.dp).fillMaxHeight()) { WeekPager(timetable, pager, now, {}, {}, Modifier.fillMaxSize()) }
                }
            }
        }
        repeat(4) { index ->
            compose.onNodeWithTag("week_pager").performTouchInput { swipeLeft(durationMillis = 220) }
            compose.runOnIdle {
                assertEquals(index + 1, pager.settledPage)
                assertFalse(pager.isScrollInProgress)
                assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
            }
        }
        repeat(4) { index ->
            compose.onNodeWithTag("week_pager").performTouchInput { swipeRight(durationMillis = 220) }
            compose.runOnIdle {
                assertEquals(3 - index, pager.settledPage)
                assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
            }
        }
        // A deliberate small drag returns fully to the current page, rather than staying halfway.
        compose.onNodeWithTag("week_pager").performTouchInput {
            swipe(Offset(width * .6f, height * .4f), Offset(width * .45f, height * .4f), durationMillis = 1000)
        }
        compose.runOnIdle { assertEquals(0, pager.settledPage); assertEquals(0f, pager.currentPageOffsetFraction, 0.001f) }
    }

    @Test fun timeDialogHasContextAndSwitchesInputWithoutLosingTime() {
        var selected = ""
        var visible by mutableStateOf(true)
        compose.setContent { PureTheme("light") { if (visible) LessonTimeDialog("08:45", "第 1 节开始时间", { visible = false }) { selected = it; visible = false } } }
        compose.onNodeWithText("选择时间").assertIsDisplayed()
        compose.onNodeWithText("第 1 节开始时间").assertIsDisplayed()
        compose.onNodeWithContentDescription("切换为键盘输入").performClick()
        compose.onNodeWithText("输入时间").assertIsDisplayed()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("09")
        fields[1].performTextReplacement("30")
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertEquals("09:30", selected) }
    }

    @Test fun deletingCoursesNeverMixesOldLayoutIndicesWithANewSnapshot() {
        val now = LocalDateTime.of(2026, 10, 19, 8, 0)
        val data = ScheduleData(Schedule(id = 999, name = "快照切换", semesterStartDate = "2026-10-19"),
            listOf(CourseWithPeriods(Course(id = 1, name = "将删除的课程"), listOf(CoursePeriod(id = 1)))))
        val cache = TimetableCache(ApplicationProvider.getApplicationContext())
        val full = runBlocking { cache.prepare(data, now.toLocalDate()) }
        val empty = runBlocking { cache.prepare(data.copy(courses = emptyList()), now.toLocalDate()) }
        var timetable by mutableStateOf(full)
        compose.setContent { PureTheme("light") {
            val pager = rememberPagerState(pageCount = { 20 })
            WeekPager(timetable, pager, now, {}, {}, Modifier.fillMaxSize())
        } }
        compose.onAllNodesWithText("将删除的课程").onFirst().assertExists()
        compose.runOnIdle { timetable = empty }
        compose.onAllNodesWithText("将删除的课程").assertCountEquals(0)
    }

    @Test fun presetThemeIsUsedWithoutSystemColors() {
        var primary = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent { PureTheme("light", theme = ThemeSettings(ThemeSource.PRESET, "rose")) {
            val color = androidx.compose.material3.MaterialTheme.colorScheme.primary
            SideEffect { primary = color }
        } }
        compose.runOnIdle { assertEquals(imageColorScheme(ThemePresets.find("rose").seed, false).primary, primary) }
    }

    @Test fun availableUpdateRemainsActionableInTheStatusCard() {
        var opened = false
        val release = AppRelease("9.0.0", "更新说明", "https://github.com/Qi-huanye/Schedule/releases/tag/v9.0.0")
        compose.setContent { PureTheme("light") { Column { AppUpdateSettings(AppUpdateState(availableRelease = release), {}, {}, { opened = true }) } } }
        compose.onNodeWithText("发现新版本 9.0.0").assertIsDisplayed()
        compose.onNodeWithText("查看更新").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun licensesAreSearchableAndFullTextIsAvailableOffline() {
        compose.setContent { PureTheme("light") { LicensesScreen {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("查找开源组件").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("查找开源组件").performTextInput("materialkolor")
        compose.onNodeWithText("com.materialkolor:material-color-utilities-android:2.0.2").performClick()
        compose.onNodeWithText("Copyright (c) 2023 Jordon de Hoog", substring = true).assertExists()
    }
}
