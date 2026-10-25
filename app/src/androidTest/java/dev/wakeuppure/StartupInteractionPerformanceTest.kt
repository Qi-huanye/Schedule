package dev.wakeuppure

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wakeuppure.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Run on an emulator/test installation: seeds synthetic data, then exercises the real root. */
@RunWith(AndroidJUnit4::class)
class StartupInteractionPerformanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun earlyAndWarmInteractionsOnDenseTimetable() {
        assumeTrue("Synthetic benchmark requires an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val app = ApplicationProvider.getApplicationContext<PureApp>()
        app.getSharedPreferences("app_updates", 0).edit().putBoolean("automatic", false).commit()
        val start = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val data = ScheduleData(Schedule(name = "启动交互性能样本", semesterStartDate = start.toString(), maxWeeks = 48, current = true),
            List(70) { i -> CourseWithPeriods(Course(id = i + 1L, name = "性能课程 $i", teacher = "教师 ${i % 10}"),
                List(12) { j -> CoursePeriod(id = i * 12L + j + 1, dayOfWeek = i % 7 + 1,
                    startSection = i % 6 * 2 + 1, endSection = i % 6 * 2 + 2,
                    startWeek = j * 2 + 1, endWeek = j * 2 + 2) }) })
        runBlocking {
            app.repository.snapshot().forEach { app.repository.deleteSchedule(it.schedule.id) }
            app.repository.importSchedules(listOf(data))
        }
        val phase = AtomicInteger(-1)
        val early = Collections.synchronizedList(mutableListOf<Long>())
        val warm = Collections.synchronizedList(mutableListOf<Long>())
        val thread = HandlerThread("frame-measurement").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (duration > 0) when (phase.get()) { 0 -> early.add(duration); 1 -> warm.add(duration) }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
                compose.waitUntil(20_000) { compose.onAllNodesWithTag("week_pager").fetchSemanticsNodes().isNotEmpty() }
                fun interact() {
                    repeat(3) {
                        compose.onNodeWithTag("week_pager").performTouchInput { swipeLeft(durationMillis = 220) }
                        compose.onNodeWithText("今日").performClick()
                        compose.onNodeWithText("我的").performClick()
                        compose.onNodeWithText("课表").performClick()
                        compose.waitForIdle()
                    }
                }
                phase.set(0); interact(); phase.set(-1)
                // Keep the process alive without blocking the UI, matching the reported warm-up.
                SystemClock.sleep(15_000)
                phase.set(1); interact(); phase.set(-1)
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            }
        } finally { thread.quitSafely() }
        fun report(name: String, samples: List<Long>) {
            val values = synchronized(samples) { samples.sorted() }
            assertTrue("Frame metrics must be captured", values.isNotEmpty())
            val p95 = values[((values.size - 1) * .95).toInt()] / 1_000_000.0
            val slow = values.count { it > 32_000_000 }
            Log.i("StartupPerformance", "build=${BuildConfig.BUILD_TYPE} sdk=${Build.VERSION.SDK_INT} phase=$name frames=${values.size} p95Ms=$p95 over32Ms=$slow")
        }
        report("early", early); report("warm", warm)
    }
}
