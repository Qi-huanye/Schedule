package dev.wakeuppure.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
val WEEK_TYPES = listOf("每周", "单周", "双周")

@Composable
fun GroupTitle(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** "Title — value ›" row; a null [onClick] renders a static row. */
@Composable
fun SettingRow(title: String, value: String? = null, titleColor: Color = Color.Unspecified, chevron: Boolean = true,
    trailing: @Composable (RowScope.() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .heightIn(min = 52.dp).padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), color = titleColor, style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        trailing?.invoke(this)
        if (chevron && onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled, Role.Switch, onChange).heightIn(min = 52.dp)
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Segments(options: List<Pair<T, String>>, selected: T, onChange: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected = value == selected, onClick = { onChange(value) }, enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, options.size), icon = {}) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
fun <T> SegmentRow(title: String, options: List<Pair<T, String>>, selected: T, onChange: (T) -> Unit, width: Dp, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Segments(options, selected, onChange, Modifier.width(width), enabled)
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true, plusEnabled: Boolean = true,
    description: String = "") {
    Row(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).height(40.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Remove, "减少$description") }
        Text(value, Modifier.widthIn(min = 40.dp), fontWeight = FontWeight.SemiBold, maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, "增加$description") }
    }
}

@Composable
fun StepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true, plusEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Stepper(value, onMinus, onPlus, minusEnabled, plusEnabled, title)
    }
}

/** Title line of a bottom sheet with an optional confirm action on the right. */
@Composable
fun SheetTitle(title: String, action: String? = null, enabled: Boolean = true, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (action != null) Button(onClick = onAction, enabled = enabled) { Text(action) }
    }
}

@Composable
fun SheetLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
