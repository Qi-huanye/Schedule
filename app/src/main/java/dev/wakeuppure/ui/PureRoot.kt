@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import dev.wakeuppure.ui.components.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private data class Destination(val route: String, val label: String, val active: ImageVector, val inactive: ImageVector)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val themeSettings by vm.themeSettings.collectAsStateWithLifecycle()
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
    PureTheme(appearance, backgroundState, themeSettings) {
        val wide = LocalWindowInfo.current.containerSize.width >= with(LocalDensity.current) { 600.dp.roundToPx() }
        val destinations = listOf(Destination("timetable", "课表", Icons.Default.CalendarMonth, Icons.Outlined.CalendarMonth),
            Destination("today", "今日", Icons.Default.Today, Icons.Outlined.Today),
            Destination("mine", "我的", Icons.Default.Person, Icons.Outlined.Person))
        val topLevel = route in destinations.map { it.route }
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        fun navigateTo(id: String) {
            nav.navigate(id) {
                popUpTo(nav.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Surface(Modifier.fillMaxSize().onSizeChanged { backgrounds.setViewport(it.width, it.height) }
            .onGloballyPositioned { onContentReady() },
            color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
            Scaffold(containerColor = Color.Transparent, bottomBar = {
                if (topLevel && !wide) NavigationBar(containerColor = backgroundChromeColor(MaterialTheme.colorScheme.surfaceContainer)) {
                    destinations.forEach { (id, label, active, inactive) ->
                        NavigationBarItem(selected = route == id, onClick = { navigateTo(id) }, icon = { DestinationIcon(route == id, active, inactive) }, label = { Text(label) })
                    }
                }
            }, floatingActionButton = {
                if (route == "timetable" && data != null) ExtendedFloatingActionButton(
                    onClick = { editCourse = null; nav.navigate("course") },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    modifier = Modifier.semantics { contentDescription = "添加课程" },
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text("添加课程") })
            }) { padding ->
                // Inner screens with their own Scaffold must not apply the system bar insets a second time.
                Row(Modifier.padding(padding).consumeWindowInsets(padding)) {
                    if (wide && topLevel) NavigationRail(containerColor = backgroundChromeColor(MaterialTheme.colorScheme.surfaceContainer),
                        header = { ScheduleMark(Modifier.padding(vertical = 16.dp).size(40.dp)) }, modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.height(24.dp))
                        destinations.forEach { (id, label, active, inactive) ->
                            NavigationRailItem(selected = route == id, onClick = { navigateTo(id) },
                                icon = { DestinationIcon(route == id, active, inactive) }, label = { Text(label) }, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    NavHost(nav, startDestination = "timetable", modifier = Modifier.weight(1f),
                        enterTransition = { fadeIn(effects) + slideInHorizontally(spatial) { it / 12 } },
                        exitTransition = { fadeOut(effects) + slideOutHorizontally(spatial) { -it / 12 } },
                        popEnterTransition = { fadeIn(effects) + slideInHorizontally(spatial) { -it / 12 } },
                        popExitTransition = { fadeOut(effects) + slideOutHorizontally(spatial) { it / 12 } }) {
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
                                { nav.navigate("times") }, openTransfer, createSchedule, { nav.navigate("about") })
                        }
                        composable("course") {
                            data?.let { CourseEditor(it, editCourse, busy, { nav.popBackStack() }, { item -> vm.deleteCourse(item.course.id); nav.popBackStack() }) { c, p, slots ->
                                vm.saveCourse(c, p, slots) { nav.popBackStack() } } }
                        }
                        composable("times") { data?.let { TimesScreen(it, busy, { nav.popBackStack() }) { s, slots -> vm.saveSchedule(s, slots) { nav.popBackStack() } } } }
                        composable("transfer") { TransferScreen(vm, all, data) { nav.popBackStack() } }
                        composable("about") { AboutScreen({ nav.popBackStack() }, { nav.navigate("licenses") }, { nav.navigate("privacy") }) }
                        composable("licenses") { LicensesScreen { nav.popBackStack() } }
                        composable("privacy") { PrivacyScreen { nav.popBackStack() } }
                    }
                }
            }
            detail?.let { item -> CourseDetailSheet(item, data?.timeSlots.orEmpty(), { detail = null }) { editCourse = item; detail = null; nav.navigate("course") } }
            if (creating) NewScheduleSheet(busy, { creating = false }) { schedule -> vm.saveSchedule(schedule, defaultTimeSlots()) { creating = false } }
            error?.let { AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("操作未完成") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("知道了") } }) }
            if (firstFrameDrawn) AppUpdateHost(updates ?: viewModel(viewModelStoreOwner = owner))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptySchedule(create: () -> Unit, import: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        FocusPanel(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            ScheduleMark(Modifier.size(80.dp))
            Text("把每一周，\n安排成喜欢的样子。", style = MaterialTheme.typography.headlineLarge)
            Text("还没有课表。创建一份，或导入已有课程。", style = MaterialTheme.typography.bodyLarge)
            Button(shapes = ButtonDefaults.shapes(), onClick = create, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("新建课表") }
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = import, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("导入课表") }
        }
    }
}
