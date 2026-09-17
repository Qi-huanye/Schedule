package dev.wakeuppure.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.ScheduleData
import dev.wakeuppure.ui.PureViewModel

@Composable
fun ReminderSettings(vm: PureViewModel, data: ScheduleData) {
    var minutes by remember(data.schedule.id, data.schedule.reminderMinutes) { mutableStateOf((data.schedule.reminderMinutes ?: 10).toString()) }
    var denied by remember { mutableStateOf(false) }
    fun save(enabled: Boolean) {
        val n = minutes.toIntOrNull()
        if (enabled && (n == null || n !in 0..1440)) { vm.error.value = "提醒时间应为 0 到 1440 分钟"; return }
        vm.saveSchedule(data.schedule.copy(reminderMinutes = if (enabled) n else null), data.timeSlots) {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> denied = !granted; if (granted) save(true) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("课前提醒", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(data.schedule.reminderMinutes != null, { enable ->
            if (enable && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else save(enable)
        })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(minutes, { minutes = it }, label = { Text("提前分钟数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else save(true) }) { Text("保存") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5,10,15,30).forEach { FilterChip(selected = minutes == it.toString(), onClick = { minutes = it.toString() }, label = { Text("$it 分") }) } }
    Text("省电模式可能延迟通知。无需常驻服务或精确闹钟权限。", style = MaterialTheme.typography.bodySmall)
    if (denied) Text("通知权限未开启", color = MaterialTheme.colorScheme.error)
}
