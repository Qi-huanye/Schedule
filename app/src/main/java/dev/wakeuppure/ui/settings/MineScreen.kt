package dev.wakeuppure.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.PureViewModel
import dev.wakeuppure.ui.background.*
import dev.wakeuppure.ui.components.*
import dev.wakeuppure.ui.update.AppUpdateSettings
import dev.wakeuppure.ui.update.AppUpdateViewModel
import java.time.LocalDate

/** 我的: the only settings page — current timetable, appearance, data and about. */
@Composable
fun MineScreen(vm: PureViewModel, data: ScheduleData?, appearance: String, backgrounds: BackgroundViewModel,
    updates: AppUpdateViewModel, onChooseImage: () -> Unit, onTimes: () -> Unit, onTransfer: () -> Unit, onNewSchedule: () -> Unit) {
    val background by backgrounds.state.collectAsState()
    val updateState by updates.state.collectAsState()
    val busy by vm.busy.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }
    var pendingReminder by remember { mutableStateOf<Int?>(null) }
    fun update(next: Schedule) { data?.let { vm.saveSchedule(next.copy(updatedAt = System.currentTimeMillis()), it.timeSlots) {} } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) data?.let { update(it.schedule.copy(reminderMinutes = pendingReminder)) } else vm.error.value = "通知权限未开启"
    }
    fun setReminder(minutes: Int?) {
        val schedule = data?.schedule ?: return
        if (minutes != null && Build.VERSION.SDK_INT >= 33) { pendingReminder = minutes; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else update(schedule.copy(reminderMinutes = minutes))
    }
    val followsBackground = background.hasImage && background.settings.courseTheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(if (LocalBackgroundActive.current) 12.dp else 0.dp)
        .background(backgroundPanelColor(), RoundedCornerShape(20.dp)).padding(bottom = 16.dp)) {
        Text("我的", Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.headlineSmall)
        if (data != null) {
            val s = data.schedule
            GroupTitle("当前课表 · ${s.name}")
            SettingRow("名称", s.name) { sheet = "name" }
            val start = runCatching { LocalDate.parse(s.semesterStartDate) }.getOrNull()
            SettingRow("学期", listOfNotNull(start?.let { "${it.monthValue}月${it.dayOfMonth}日开学" }, "${s.maxWeeks} 周").joinToString(" · ")) { sheet = "term" }
            SettingRow("作息时间", "每天 ${data.timeSlots.size} 节", onClick = onTimes)
            SwitchRow("显示周末", s.showWeekend, { update(s.copy(showWeekend = it)) }, enabled = !busy)
            SegmentRow("每周第一天", listOf(1 to "周一", 7 to "周日"), s.firstDay, { update(s.copy(firstDay = it)) }, 132.dp, enabled = !busy)
            SettingRow("课程配色", if (followsBackground) "跟随背景" else null,
                trailing = { if (!followsBackground) Swatches(paletteColors(data).take(4).map(::hexColor)) }) { sheet = "palette" }
            SettingRow("课前提醒", s.reminderMinutes?.let { "提前 $it 分钟" } ?: "关闭") { sheet = "reminder" }
            SettingRow("新建课表", onClick = onNewSchedule)
            SettingRow("删除此课表", titleColor = MaterialTheme.colorScheme.error, chevron = false, onClick = { sheet = "delete" })
        } else {
            GroupTitle("课表")
            SettingRow("新建课表", onClick = onNewSchedule)
            SettingRow("导入课表", onClick = onTransfer)
        }
        GroupTitle("外观")
        SegmentRow("主题", listOf("system" to "系统", "light" to "浅色", "dark" to "深色"), appearance, vm::setAppearance, 200.dp)
        BackgroundSettingsSection(background, onChooseImage, backgrounds::clearBackground, backgrounds::setImageTheme,
            backgrounds::previewBlur, backgrounds::commitBlur, backgrounds::setFocus)
        GroupTitle("数据")
        SettingRow("导入与导出", onClick = onTransfer)
        GroupTitle("关于")
        AppUpdateSettings(updateState, updates::setAutoCheckEnabled) { updates.checkForUpdates(manual = true) }
        SettingRow("开源许可与隐私") { sheet = "licenses" }
    }
    val close = { sheet = null }
    when (sheet) {
        "licenses" -> LicensesDialog(close)
        else -> if (data != null) when (sheet) {
            "name" -> RenameDialog(data.schedule.name, close) { update(data.schedule.copy(name = it)); close() }
            "term" -> TermSheet(data, busy, close) { schedule, preserve, offset -> vm.saveSchedule(schedule, data.timeSlots, preserve, offset) { close() } }
            "palette" -> PaletteSheet(if (followsBackground) FOLLOW_BACKGROUND else data.schedule.colorPalette,
                background.colors?.lightCourses?.takeIf { background.hasImage }, close) { choice ->
                close()
                if (choice == FOLLOW_BACKGROUND) backgrounds.setCourseTheme(true)
                else {
                    if (background.settings.courseTheme) backgrounds.setCourseTheme(false)
                    if (choice != data.schedule.colorPalette) update(data.schedule.copy(colorPalette = choice))
                }
            }
            "reminder" -> ReminderSheet(data.schedule.reminderMinutes, close) { close(); setReminder(it) }
            "delete" -> AlertDialog(onDismissRequest = close, title = { Text("删除 ${data.schedule.name}？") },
                text = { Text("该课表及其所有课程将被删除。") },
                confirmButton = { TextButton(onClick = { close(); vm.deleteSchedule(data.schedule.id) }) { Text("删除") } },
                dismissButton = { TextButton(onClick = close) { Text("取消") } })
        }
    }
}

internal const val FOLLOW_BACKGROUND = "background"

private fun paletteColors(data: ScheduleData): List<String> =
    CoursePalettes.find(data.schedule.colorPalette)?.colors ?: data.courses.map { it.course.color }.distinct().ifEmpty { CoursePalettes.all.first().colors }

internal fun hexColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

@Composable
internal fun Swatches(colors: List<Color>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { Box(Modifier.size(16.dp).background(it, CircleShape)) }
    }
}
