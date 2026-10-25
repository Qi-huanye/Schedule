@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.wakeuppure.ui.background.backgroundPanelColor

val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
val WEEK_TYPES = listOf("每周", "单周", "双周")

@Composable
fun GroupTitle(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GroupTitle(title)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
fun SettingPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth()
        .background(backgroundPanelColor(MaterialTheme.colorScheme.surfaceContainerLow), shape)
        .clip(shape), content = content)
}

/** "Title — value ›" row; a null [onClick] renders a static row. */
@Composable
fun SettingRow(title: String, value: String? = null, titleColor: Color = Color.Unspecified, chevron: Boolean = true,
    trailing: @Composable (RowScope.() -> Unit)? = null, leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = if (value == null) 56.dp else 72.dp)
        .background(backgroundPanelColor(MaterialTheme.colorScheme.surfaceContainerLow), shape).clip(shape)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(24.dp),
            tint = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = titleColor, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
        if (chevron && onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, subtitle: String? = null,
    leadingIcon: ImageVector? = null) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .heightIn(min = if (subtitle == null) 56.dp else 72.dp)
        .background(backgroundPanelColor(MaterialTheme.colorScheme.surfaceContainerLow), shape).clip(shape)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> Segments(options: List<Pair<T, String>>, selected: T, onChange: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, (value, label) ->
            ToggleButton(checked = value == selected, onCheckedChange = { onChange(value) }, enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton }, contentPadding = PaddingValues(horizontal = 8.dp),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }) { Text(label) }
        }
    }
}

@Composable
fun <T> SegmentRow(title: String, options: List<Pair<T, String>>, selected: T, onChange: (T) -> Unit, width: Dp, enabled: Boolean = true) {
    SettingPanel {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Segments(options, selected, onChange, Modifier.widthIn(min = width).fillMaxWidth(), enabled)
        }
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true, plusEnabled: Boolean = true,
    description: String = "") {
    Row(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Remove, "减少$description") }
        Text(value, Modifier.widthIn(min = 40.dp), fontWeight = FontWeight.SemiBold, maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Add, "增加$description") }
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
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        if (action != null) Button(shapes = ButtonDefaults.shapes(), onClick = onAction, enabled = enabled) { Text(action) }
    }
}

@Composable
fun SheetLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
