package dev.wakeuppure.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.course.*
import dev.wakeuppure.ui.settings.*
import dev.wakeuppure.ui.timetable.TimetableScreen
import dev.wakeuppure.ui.today.TodayScreen
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureRoot(vm: PureViewModel = viewModel()) {
    val all by vm.schedules.collectAsState()
    val data = all.firstOrNull { it.schedule.current } ?: all.firstOrNull()
    val appearance by vm.appearance.collectAsState()
    val error by vm.error.collectAsState()
    val busy by vm.busy.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(30_000) } }
    var editCourse by remember { mutableStateOf<CourseWithPeriods?>(null) }
    var detail by remember { mutableStateOf<CourseWithPeriods?>(null) }
    var editSchedule by remember { mutableStateOf<ScheduleData?>(null) }
    var deleteCourse by remember { mutableStateOf<CourseWithPeriods?>(null) }
    var deleteSchedule by remember { mutableStateOf<ScheduleData?>(null) }
    var scheduleMenu by remember { mutableStateOf(false) }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "timetable"
    PureTheme(appearance) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = {
                if (route in listOf("timetable", "today", "mine")) TopAppBar(title = {
                    Column(Modifier.clickable { scheduleMenu = true }) {
                        Text(data?.schedule?.name ?: "Schedule", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Schedule", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DropdownMenu(scheduleMenu, { scheduleMenu = false }) {
                            all.forEach { item -> DropdownMenuItem(text = { Text(item.schedule.name) }, onClick = { vm.select(item.schedule.id); scheduleMenu = false }) }
                            DropdownMenuItem(text = { Text("新建课表") }, onClick = { editSchedule = null; scheduleMenu = false; nav.navigate("schedule") })
                        }
                    }
                }, actions = { IconButton(onClick = { editSchedule = data; nav.navigate("schedule") }) { Icon(Icons.Default.Settings, "课表设置") } })
            }, bottomBar = {
                if (route in listOf("timetable", "today", "mine")) NavigationBar {
                    listOf(Triple("timetable", "课表", Icons.Default.CalendarMonth), Triple("today", "今日", Icons.Default.Today), Triple("mine", "我的", Icons.Default.PersonOutline)).forEach { (id, label, icon) ->
                        NavigationBarItem(selected = route == id, onClick = { nav.navigate(id) { popUpTo("timetable"); launchSingleTop = true } }, icon = { Icon(icon, label) }, label = { Text(label) })
                    }
                }
            }, floatingActionButton = {
                if (route == "timetable" && data != null) FloatingActionButton(onClick = { editCourse = null; nav.navigate("course") }) { Icon(Icons.Default.Add, "添加课程") }
            }) { padding ->
                NavHost(nav, startDestination = "timetable", modifier = Modifier.padding(padding)) {
                    composable("timetable") {
                        if (data == null) EmptySchedule { editSchedule = null; nav.navigate("schedule") }
                        else TimetableScreen(data, now, { detail = it }, { editCourse = it; nav.navigate("course") })
                    }
                    composable("today") { if (data == null) EmptySchedule { nav.navigate("schedule") } else TodayScreen(data, now) { detail = it } }
                    composable("mine") {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("我的课表", style = MaterialTheme.typography.titleLarge)
                            all.forEach { item ->
                                ListItem(headlineContent = { Text(item.schedule.name) }, supportingContent = { Text("${item.courses.size} 门课程 · ${item.schedule.maxWeeks} 周") },
                                    leadingContent = { RadioButton(selected = item.schedule.current, onClick = { vm.select(item.schedule.id) }) },
                                    trailingContent = { Row {
                                        IconButton(onClick = { editSchedule = item; nav.navigate("schedule") }) { Icon(Icons.Default.Edit, "编辑课表") }
                                        IconButton(onClick = { deleteSchedule = item }) { Icon(Icons.Default.DeleteOutline, "删除课表") }
                                    } })
                            }
                            OutlinedButton(onClick = { editSchedule = null; nav.navigate("schedule") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("新建课表") }
                            HorizontalDivider()
                            Text("外观", style = MaterialTheme.typography.titleMedium)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEachIndexed { i, (value, label) ->
                                    SegmentedButton(selected = appearance == value, onClick = { vm.setAppearance(value) }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                                }
                            }
                            Button(onClick = { nav.navigate("transfer") }, modifier = Modifier.fillMaxWidth()) { Text("导入 / 导出 / 数据备份") }
                            data?.let { ReminderSettings(vm, it) }
                            HorizontalDivider()
                            Text("关于 Schedule", style = MaterialTheme.typography.titleMedium)
                            Text("0.2.0 · 开源课程表\n无广告 · 无账号 · 无追踪\n课表保存在本机。仅主动使用分享口令导入时访问网络。", style = MaterialTheme.typography.bodyMedium)
                            Text("协议算法参考 WakeUpDecoder（Apache-2.0）。架构与格式研究参考 Sleepy；更多信息见项目 README。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    composable("course") { data?.let { CourseEditor(it, editCourse, busy, { nav.popBackStack() }) { c, p, slots -> vm.saveCourse(c, p, slots) { nav.popBackStack() } } } }
                    composable("schedule") { ScheduleEditor(editSchedule, busy, { nav.popBackStack() }) { s, t, preserveDates, weekOffset -> vm.saveSchedule(s, t, preserveDates, weekOffset) { nav.popBackStack() } } }
                    composable("transfer") { TransferScreen(vm, all, data) { nav.popBackStack() } }
                }
            }
            detail?.let { item -> AlertDialog(onDismissRequest = { detail = null }, title = { Text(item.course.name) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.course.teacher.isNotBlank()) Text("教师 · ${item.course.teacher}")
                    item.periods.forEach { p -> Text("周${listOf("一","二","三","四","五","六","日")[p.dayOfWeek-1]}  ${p.startSection}-${p.endSection}节\n第 ${p.startWeek}-${p.endWeek} 周 · ${listOf("每周","单周","双周")[p.weekType.ordinal]}\n${p.classroom.ifBlank { item.course.classroom }}") }
                    if (item.course.note.isNotBlank()) Text(item.course.note)
                }
            }, confirmButton = { TextButton(onClick = { editCourse = item; detail = null; nav.navigate("course") }) { Text("编辑") } },
                dismissButton = { TextButton(onClick = { deleteCourse = item; detail = null }) { Text("删除") } }) }
            deleteCourse?.let { item -> AlertDialog(onDismissRequest = { deleteCourse = null }, title = { Text("删除 ${item.course.name}？") }, text = { Text("这门课程的全部上课时段将被删除。") }, confirmButton = { TextButton(onClick = { vm.deleteCourse(item.course.id); deleteCourse = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteCourse = null }) { Text("取消") } }) }
            deleteSchedule?.let { item -> AlertDialog(onDismissRequest = { deleteSchedule = null }, title = { Text("删除 ${item.schedule.name}？") }, text = { Text("该课表及其所有课程将被删除。") }, confirmButton = { TextButton(onClick = { vm.deleteSchedule(item.schedule.id); deleteSchedule = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteSchedule = null }) { Text("取消") } }) }
            error?.let { AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("操作未完成") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("知道了") } }) }
        }
    }
}

@Composable
private fun EmptySchedule(create: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CalendarMonth, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("还没有课表", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = create) { Text("新建课表") }
    }
}
