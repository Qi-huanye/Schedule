@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.wakeuppure.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wakeuppure.domain.model.InterfaceSize
import dev.wakeuppure.ui.InterfaceScale
import dev.wakeuppure.ui.components.SheetTitle
import kotlin.math.roundToInt

@Composable
internal fun InterfaceSizeSheet(current: Int, showWeekend: Boolean, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var percent by rememberSaveable { mutableIntStateOf(InterfaceSize.clamp(current)) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SheetTitle("界面大小", "应用") { onConfirm(percent) }
            Text("调整应用内的文字、间距与组件大小。100% 跟随系统设置。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                // Keep the preview in physical pixels while its content uses the selected density.
                // The containing sheet keeps its current size until Apply is pressed.
                InterfaceScale(percent) {
                    TimetableSizePreview(showWeekend)
                }
            }
            Text("$percent%", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Column {
                Slider(value = percent.toFloat(), onValueChange = {
                    percent = InterfaceSize.clamp((it / InterfaceSize.STEP_PERCENT).roundToInt() * InterfaceSize.STEP_PERCENT)
                }, valueRange = InterfaceSize.MIN_PERCENT.toFloat()..InterfaceSize.MAX_PERCENT.toFloat(),
                    steps = (InterfaceSize.MAX_PERCENT - InterfaceSize.MIN_PERCENT) / InterfaceSize.STEP_PERCENT - 1,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "界面缩放比例" })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("较小", style = MaterialTheme.typography.labelMedium)
                    Text("较大", style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = { percent = InterfaceSize.DEFAULT_PERCENT }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("恢复跟随系统") }
        }
    }
}

@Composable
private fun TimetableSizePreview(showWeekend: Boolean) {
    val days = if (showWeekend) 7 else 5
    val rowHeight = 72.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("课表预览", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Spacer(Modifier.width(32.dp))
            List(days) { "周${"一二三四五六日"[it]}" }.forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, fontSize = 10.sp, maxLines = 1) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Column(Modifier.width(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("1", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("08:00", fontSize = 9.sp, maxLines = 1)
            }
            repeat(days) { day ->
                val course = when (day) { 0 -> "高等数学"; 2 -> "大学英语"; else -> null }
                Surface(modifier = Modifier.weight(1f).heightIn(min = rowHeight), shape = RoundedCornerShape(12.dp),
                    color = if (course == null) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.secondaryContainer) {
                    if (course != null) Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(course, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Text("张老师", fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("A201", fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
