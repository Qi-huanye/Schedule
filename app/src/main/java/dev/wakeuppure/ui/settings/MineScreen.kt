package dev.wakeuppure.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
    updates: AppUpdateViewModel, onChooseImage: () -> Unit, onTimes: () -> Unit, onTransfer: () -> Unit, onNewSchedule: () -> Unit,
    onAbout: () -> Unit) {
    val background by backgrounds.state.collectAsState()
    val updateState by updates.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val theme by vm.themeSettings.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }
    var pendingReminder by remember { mutableStateOf<Int?>(null) }
    // Each quick change applies only its own field to the latest stored schedule, so rapid changes never overwrite each other.
    fun update(change: (Schedule) -> Schedule) { data?.let { vm.updateSchedule(it.schedule.id, change) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val minutes = pendingReminder
        if (granted) update { it.copy(reminderMinutes = minutes) } else vm.error.value = "通知权限未开启"
    }
    fun setReminder(minutes: Int?) {
        if (data == null) return
        if (minutes != null && Build.VERSION.SDK_INT >= 33) { pendingReminder = minutes; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else update { it.copy(reminderMinutes = minutes) }
    }
    val followsBackground = background.hasImage && background.settings.courseTheme
    // The photo remains visible between independent setting cards, as on Today.
    Column(Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        PageHeading("我的", "让课表更合你的习惯") { ScheduleMark(Modifier.size(48.dp)) }
        if (data != null) {
            val s = data.schedule
            SettingsSection("当前课表") {
                SettingRow("名称", s.name) { sheet = "name" }
                val start = runCatching { LocalDate.parse(s.semesterStartDate) }.getOrNull()
                SettingRow("学期", listOfNotNull(start?.let { "${it.monthValue}月${it.dayOfMonth}日开学" }, "共 ${s.maxWeeks} 周").joinToString("，")) { sheet = "term" }
                SettingRow("作息时间", "每天 ${data.timeSlots.size} 节", onClick = onTimes)
                SwitchRow("显示周末", s.showWeekend, { value -> update { it.copy(showWeekend = value) } })
                SegmentRow("每周第一天", listOf(1 to "周一", 7 to "周日"), s.firstDay, { value -> update { it.copy(firstDay = value) } }, 132.dp)
                SettingRow("课程配色", if (followsBackground) "跟随背景" else null,
                    trailing = { if (!followsBackground) Swatches(paletteColors(data).take(4).map(::hexColor)) }) { sheet = "palette" }
                SettingRow("课前提醒", s.reminderMinutes?.let { "提前 $it 分钟" } ?: "关闭") { sheet = "reminder" }
                SettingRow("新建课表", onClick = onNewSchedule)
                SettingRow("删除此课表", titleColor = MaterialTheme.colorScheme.error, chevron = false, onClick = { sheet = "delete" })
            }
        } else {
            SettingsSection("课表") {
                SettingRow("新建课表", onClick = onNewSchedule)
                SettingRow("导入课表", onClick = onTransfer)
            }
        }
        SettingsSection("外观") {
            SegmentRow("外观模式", listOf("system" to "系统", "light" to "浅色", "dark" to "深色"), appearance, vm::setAppearance, 200.dp)
            val imageTheme = background.hasImage && background.settings.imageTheme
            SwitchRow("系统动态取色", !imageTheme && theme.source == ThemeSource.SYSTEM && Build.VERSION.SDK_INT >= 31,
                vm::setSystemColors, enabled = !imageTheme && Build.VERSION.SDK_INT >= 31,
                subtitle = when {
                    imageTheme -> "正在使用背景图片取色，关闭“主题跟随背景”后可更改"
                    Build.VERSION.SDK_INT < 31 -> "Android 12 及以上可用，当前使用预设主题色"
                    else -> "从系统壁纸生成应用配色"
                })
            SettingRow("预设主题色", when {
                imageTheme -> "关闭背景取色后可选择"
                theme.source == ThemeSource.SYSTEM && Build.VERSION.SDK_INT >= 31 -> "选择预设可替代系统配色"
                else -> ThemePresets.find(theme.preset).name
            },
                onClick = if (imageTheme) null else ({ sheet = "theme" }))
            BackgroundSettingsSection(background, onChooseImage, backgrounds::clearBackground, backgrounds::setImageTheme,
                backgrounds::previewBlur, backgrounds::commitBlur, backgrounds::setFocus)
        }
        SettingsSection("数据") {
            SettingRow("导入与导出", onClick = onTransfer)
        }
        SettingsSection("关于") {
            AppUpdateSettings(updateState, updates::setAutoCheckEnabled, { updates.checkForUpdates(manual = true) }, updates::showAvailableUpdate)
            SettingRow("关于 Schedule", "源代码、开源许可与隐私", onClick = onAbout)
        }
    }
    val close = { sheet = null }
    when (sheet) {
        "theme" -> ThemePresetSheet(theme, close) { vm.setThemePreset(it); close() }
        else -> if (data != null) when (sheet) {
            "name" -> RenameDialog(data.schedule.name, close) { name -> update { it.copy(name = name) }; close() }
            "term" -> TermSheet(data, busy, close) { schedule, preserve, offset -> vm.saveSchedule(schedule, data.timeSlots, preserve, offset) { close() } }
            "palette" -> PaletteSheet(if (followsBackground) FOLLOW_BACKGROUND else data.schedule.colorPalette,
                background.colors?.lightCourses?.takeIf { background.hasImage }, close) { choice ->
                close()
                if (choice == FOLLOW_BACKGROUND) backgrounds.setCourseTheme(true)
                else {
                    if (background.settings.courseTheme) backgrounds.setCourseTheme(false)
                    if (choice != data.schedule.colorPalette) update { it.copy(colorPalette = choice) }
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
