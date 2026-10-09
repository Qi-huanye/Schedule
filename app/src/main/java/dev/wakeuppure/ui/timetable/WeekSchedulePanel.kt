package dev.wakeuppure.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.ScheduleData
import dev.wakeuppure.ui.components.*

/** Opened from the timetable title: jump to any week, then switch, create or import timetables. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeekSchedulePanel(data: ScheduleData, all: List<ScheduleData>, week: Int, current: Int, onDismiss: () -> Unit,
    onWeek: (Int) -> Unit, onSelect: (Long) -> Unit, onNewSchedule: () -> Unit, onImport: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetLabel("周次")
            SelectGrid((1..data.schedule.maxWeeks).toList(), 5, { if (it == week) CellState.EDGE else CellState.NONE }, onWeek,
                marked = { it == current }, describe = { if (it == current) "第 $it 周（本周）" else "第 $it 周" })
            SheetLabel("课表")
            all.forEach { item ->
                ListItem(headlineContent = { Text(item.schedule.name) }, supportingContent = { Text("${item.courses.size} 门课程") },
                    leadingContent = {
                        if (item.schedule.id == data.schedule.id) Icon(Icons.Default.Check, "当前课表", tint = MaterialTheme.colorScheme.primary)
                        else Spacer(Modifier.size(24.dp))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.then(if (item.schedule.id != data.schedule.id) Modifier.clickable { onSelect(item.schedule.id) } else Modifier))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onNewSchedule, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("新建")
                }
                FilledTonalButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("导入")
                }
            }
        }
    }
}
