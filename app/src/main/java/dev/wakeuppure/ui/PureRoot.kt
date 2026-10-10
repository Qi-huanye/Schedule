package dev.wakeuppure.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.background.*
import dev.wakeuppure.ui.course.*
import dev.wakeuppure.ui.settings.*
import dev.wakeuppure.ui.timetable.TimetableScreen
import dev.wakeuppure.ui.today.TodayScreen
import dev.wakeuppure.ui.update.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureRoot(vm: PureViewModel = viewModel(), updates: AppUpdateViewModel? = null,
    backgrounds: BackgroundViewModel = viewModel(), onContentReady: () -> Unit = {}) {
    val opening by remember(vm, backgrounds) { startupState(vm, backgrounds) }.collectAsStateWithLifecycle(initialValue = null)
    // Register before the readiness gate: Android can restore a pending image-picker result here.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(backgrounds::importImage)
    }
    val state = opening
    if (state == null) {
        Box(Modifier.fillMaxSize().onSizeChanged { backgrounds.setViewport(it.width, it.height) })
        return
    }
    val all = state.schedules.schedules
    val data = all.firstOrNull { it.schedule.current } ?: all.firstOrNull()
    val appearance = state.appearance
    val error by vm.error.collectAsState()
    val busy by vm.busy.collectAsState()
    val backgroundState = state.background
    val owner = requireNotNull(LocalViewModelStoreOwner.current)
    var firstFrameDrawn by remember { mutableStateOf(false) }
    if (!firstFrameDrawn) AfterFirstDraw { firstFrameDrawn = true }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(30_000) } }
    LaunchedEffect(now.toLocalDate()) { vm.setDate(now.toLocalDate()) }
    var editCourse by remember { mutableStateOf<CourseWithPeriods?>(null) }
    var detail by remember { mutableStateOf<CourseWithPeriods?>(null) }
    var creating by remember { mutableStateOf(false) }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "timetable"
    val createSchedule = { creating = true }
    val openTransfer = { nav.navigate("transfer") }
    PureTheme(appearance, backgroundState) {
        Surface(Modifier.fillMaxSize().onSizeChanged { backgrounds.setViewport(it.width, it.height) }
            .onGloballyPositioned { onContentReady() },
            color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
            Scaffold(containerColor = Color.Transparent, bottomBar = {
                if (route in listOf("timetable", "today", "mine")) NavigationBar(containerColor = backgroundChromeColor(MaterialTheme.colorScheme.surfaceContainer)) {
                    listOf(Triple("timetable", "课表", Icons.Default.CalendarMonth), Triple("today", "今日", Icons.Default.Today), Triple("mine", "我的", Icons.Default.PersonOutline)).forEach { (id, label, icon) ->
                        NavigationBarItem(selected = route == id, onClick = { nav.navigate(id) { popUpTo("timetable"); launchSingleTop = true } }, icon = { Icon(icon, label) }, label = { Text(label) })
                    }
                }
            }, floatingActionButton = {
                if (route == "timetable" && data != null) FloatingActionButton(onClick = { editCourse = null; nav.navigate("course") }) { Icon(Icons.Default.Add, "添加课程") }
            }) { padding ->
                // Inner screens with their own Scaffold must not apply the system bar insets a second time.
                NavHost(nav, startDestination = "timetable", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                    composable("timetable") {
                        if (data == null) EmptySchedule(createSchedule, openTransfer)
                        else TimetableScreen(requireNotNull(state.schedules.timetable), all, now, { detail = it }, { editCourse = it; nav.navigate("course") }, vm::select, createSchedule, openTransfer,
                            prefetchEnabled = firstFrameDrawn)
                    }
                    composable("today") {
                        if (data == null) EmptySchedule(createSchedule, openTransfer) else TodayScreen(data, now) { detail = it }
                    }
                    composable("mine") {
                        val settingsUpdates = updates ?: viewModel<AppUpdateViewModel>(viewModelStoreOwner = owner)
                        MineScreen(vm, data, appearance, backgrounds, settingsUpdates, { imagePicker.launch(arrayOf("image/*")) },
                            { nav.navigate("times") }, openTransfer, createSchedule)
                    }
                    composable("course") {
                        data?.let { CourseEditor(it, editCourse, busy, { nav.popBackStack() }, { item -> vm.deleteCourse(item.course.id); nav.popBackStack() }) { c, p, slots ->
                            vm.saveCourse(c, p, slots) { nav.popBackStack() } } }
                    }
                    composable("times") { data?.let { TimesScreen(it, busy, { nav.popBackStack() }) { s, slots -> vm.saveSchedule(s, slots) { nav.popBackStack() } } } }
                    composable("transfer") { TransferScreen(vm, all, data) { nav.popBackStack() } }
                }
            }
            detail?.let { item -> CourseDetailSheet(item, data?.timeSlots.orEmpty(), { detail = null }) { editCourse = item; detail = null; nav.navigate("course") } }
            if (creating) NewScheduleSheet(busy, { creating = false }) { schedule -> vm.saveSchedule(schedule, defaultTimeSlots()) { creating = false } }
            error?.let { AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("操作未完成") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("知道了") } }) }
            if (firstFrameDrawn) AppUpdateHost(updates ?: viewModel(viewModelStoreOwner = owner))
        }
    }
}

@Composable
private fun EmptySchedule(create: () -> Unit, import: () -> Unit) {
    Column(Modifier.fillMaxSize().background(backgroundPanelColor()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CalendarMonth, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("还没有课表", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = create) { Text("新建课表") }
            FilledTonalButton(onClick = import) { Text("导入课表") }
        }
    }
}
