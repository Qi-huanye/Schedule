package dev.wakeuppure.ui.background

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

val LocalBackgroundActive = staticCompositionLocalOf { false }
val LocalCourseColors = staticCompositionLocalOf<List<Int>?> { null }

@Composable
fun BackgroundContainer(state: BackgroundUiState?, dark: Boolean, content: @Composable () -> Unit) {
    val active = state?.hasImage == true
    val courses = state?.colors?.takeIf { active && state.settings.courseTheme }?.let {
        if (dark) it.darkCourses else it.lightCourses
    }
    CompositionLocalProvider(LocalBackgroundActive provides active, LocalCourseColors provides courses) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (active) {
                val image = remember(state!!.bitmap) { state.bitmap!!.asImageBitmap() }
                Image(image, null, Modifier.matchParentSize().testTag("app_background_image"), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.30f else 0.18f)))
            }
            content()
        }
    }
}

/** Text panels stay readable even on very light or very dark photos. */
@Composable
fun backgroundPanelColor(default: Color = Color.Transparent): Color =
    if (LocalBackgroundActive.current) MaterialTheme.colorScheme.surface.copy(alpha = 0.94f) else default

@Composable
fun backgroundScreenColor(): Color = backgroundPanelColor(MaterialTheme.colorScheme.background)

/** A visual override only: persisted Course.color and exported data are untouched. */
@Composable
fun courseDisplayColors(courseId: Long, storedColor: String): Pair<Color, Color> {
    val palette = LocalCourseColors.current
    if (!palette.isNullOrEmpty()) {
        val argb = palette[Math.floorMod(courseId, palette.size.toLong()).toInt()]
        return Color(argb) to Color(contrastingTextColor(argb))
    }
    val base = runCatching { Color(android.graphics.Color.parseColor(storedColor)) }.getOrDefault(Color(0xFFC1E8DE))
    if (LocalBackgroundActive.current) {
        // Imported courses may contain translucent ARGB colors. Give their text a reliable
        // backing over photos, while leaving the stored color and ordinary rendering intact.
        val opaque = base.compositeOver(MaterialTheme.colorScheme.surface)
        return opaque to Color(contrastingTextColor(opaque.toArgb()))
    }
    return base to if (base.luminance() < 0.35f) Color.White else Color(0xFF142925)
}

@Composable
fun BackgroundSettingsSection(
    state: BackgroundUiState,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onImageThemeChanged: (Boolean) -> Unit,
    onCourseThemeChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("应用背景", style = MaterialTheme.typography.titleSmall)
        state.bitmap?.let { bitmap ->
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(image, "当前背景预览", Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onChoose, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.hasImage) "更换背景图片" else "选择背景图片")
            }
            if (state.hasImage) TextButton(onClick = onRemove, enabled = !state.busy) { Text("移除背景") }
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在处理背景…", style = MaterialTheme.typography.bodySmall)
        }
        BackgroundSwitch("主题跟随背景", "根据图片自动生成浅色和深色主题", state.settings.imageTheme,
            state.hasImage && !state.busy, onImageThemeChanged)
        BackgroundSwitch("课程跟随背景配色", "为课程自动配色，关闭后恢复原有颜色", state.settings.courseTheme,
            state.hasImage && !state.busy, onCourseThemeChanged)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("图片仅保存在本机，不会上传；课表备份不包含背景图片。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackgroundSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled, Role.Switch, onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
