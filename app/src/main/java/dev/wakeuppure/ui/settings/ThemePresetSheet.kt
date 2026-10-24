@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.wakeuppure.ui.settings

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.background.imageColorScheme
import dev.wakeuppure.ui.components.SheetTitle

@Composable
internal fun ThemePresetSheet(theme: ThemeSettings, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle("预设主题色")
            Text("选择后使用此配色，浅色与深色外观会自动适配。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
            ThemePresets.all.forEach { preset ->
                val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val scheme = remember(preset, dark) { imageColorScheme(preset.seed, dark) }
                val selected = theme.source == ThemeSource.PRESET && theme.preset == preset.id
                ColorPaletteOption(preset.name, listOf(scheme.primary, scheme.primaryContainer, scheme.tertiary), selected) { onSelect(preset.id) }
            }
        }
    }
}
