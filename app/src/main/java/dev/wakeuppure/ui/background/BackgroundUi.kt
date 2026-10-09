package dev.wakeuppure.ui.background

import android.os.Build
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.BackgroundFocus
import kotlin.math.roundToInt

val LocalBackgroundActive = staticCompositionLocalOf { false }
val LocalCourseColors = staticCompositionLocalOf<List<Int>?> { null }
val LocalBackgroundLayers = staticCompositionLocalOf<BackgroundLayers?> { null }

/**
 * One visual hierarchy: photo → scrim → content panels → cards → chrome. A single blur level
 * moves every layer together, from the photo as-is (0) to a softened, course-first page (1),
 * so screens read these values instead of choosing their own transparency.
 */
data class BackgroundLayers(
    val blur: Dp,
    /** Scrim over the timetable body. */
    val lightScrim: Float,
    val darkScrim: Float,
    /** Scrim behind the status bar, header and navigation; it fades into the body, no hard edges. */
    val edgeScrim: Float,
    /** Cards and forms on Today / My pages. */
    val panel: Float,
    /** The time rail beside the grid; it fades out as the scrim takes over. */
    val rail: Float,
    val course: Float,
    val ghost: Float,
) {
    fun scrim(dark: Boolean) = if (dark) darkScrim else lightScrim

    companion object {
        private val MAX_BLUR = 16.dp
        // API < 31 cannot blur, so a denser scrim stands in for the missing softening.
        private val blurFallback = if (Build.VERSION.SDK_INT >= 31) 0f else 0.08f

        fun of(level: Float): BackgroundLayers {
            val t = level.coerceIn(0f, 1f)
            fun mix(clear: Float, soft: Float) = clear + (soft - clear) * t
            return BackgroundLayers(
                blur = MAX_BLUR * t,
                lightScrim = mix(0.12f, 0.70f) + blurFallback * t,
                darkScrim = mix(0.30f, 0.66f) + blurFallback * t,
                edgeScrim = mix(0.74f, 0.84f),
                panel = mix(0.86f, 0.92f),
                rail = mix(0.62f, 0f),
                course = mix(0.93f, 1f),
                ghost = mix(0.72f, 0.55f),
            )
        }
    }
}

private fun BackgroundFocus.alignment() = BiasAlignment(0f, when (this) {
    BackgroundFocus.TOP -> -1f
    BackgroundFocus.CENTER -> 0f
    BackgroundFocus.BOTTOM -> 1f
})

@Composable
fun BackgroundContainer(state: BackgroundUiState?, dark: Boolean, content: @Composable () -> Unit) {
    val active = state?.hasImage == true
    val courses = state?.colors?.takeIf { active && state.settings.courseTheme }?.let {
        if (dark) it.darkCourses else it.lightCourses
    }
    val layers = state?.settings?.blur?.takeIf { active }?.let(BackgroundLayers::of)
    CompositionLocalProvider(
        LocalBackgroundActive provides active,
        LocalCourseColors provides courses,
        LocalBackgroundLayers provides layers,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (active && layers != null) {
                val image = remember(state!!.bitmap) { state.bitmap!!.asImageBitmap() }
                val softened = if (layers.blur > 0.5.dp) {
                    // Slight overscan hides the transparent fringe that blur leaves at the edges.
                    Modifier.graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }
                        .blur(layers.blur, BlurredEdgeTreatment.Rectangle)
                } else Modifier
                Image(image, null, Modifier.matchParentSize().then(softened).testTag("app_background_image"),
                    contentScale = ContentScale.Crop, alignment = state.settings.focus.alignment())
                val surface = MaterialTheme.colorScheme.surface
                val body = surface.copy(alpha = layers.scrim(dark))
                val edge = surface.copy(alpha = maxOf(layers.edgeScrim, layers.scrim(dark)))
                // One continuous layer: denser behind the bars, easing into the timetable body.
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to edge, 0.15f to edge, 0.26f to body, 0.82f to body, 0.92f to edge, 1f to edge,
                )))
            }
            content()
        }
    }
}

/** Text panels stay readable even on very light or very dark photos. */
@Composable
fun backgroundPanelColor(default: Color = Color.Transparent): Color =
    LocalBackgroundLayers.current?.let { MaterialTheme.colorScheme.surface.copy(alpha = it.panel) } ?: default

/** Top bars and navigation sit directly on the gradient scrim, so they never form a box. */
@Composable
fun backgroundChromeColor(default: Color = Color.Transparent): Color =
    if (LocalBackgroundLayers.current != null) Color.Transparent else default

/** The timetable's time rail. */
@Composable
fun backgroundRailColor(): Color =
    LocalBackgroundLayers.current?.let { MaterialTheme.colorScheme.surface.copy(alpha = it.rail) } ?: Color.Transparent

@Composable
fun backgroundScreenColor(): Color = backgroundPanelColor(MaterialTheme.colorScheme.background)

/** Off-week cards recede: a pale neutral wash instead of a heavy gray block. */
@Composable
fun ghostCourseColor(): Color {
    val layers = LocalBackgroundLayers.current
    return if (layers != null) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = layers.ghost)
    else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
}

/** A visual override only: persisted Course.color and exported data are untouched. */
@Composable
fun courseDisplayColors(courseId: Long, storedColor: String): Pair<Color, Color> {
    val palette = LocalCourseColors.current
    val cardAlpha = LocalBackgroundLayers.current?.course ?: 1f
    if (!palette.isNullOrEmpty()) {
        val argb = palette[Math.floorMod(courseId, palette.size.toLong()).toInt()]
        return Color(argb).copy(alpha = cardAlpha) to Color(contrastingTextColor(argb))
    }
    val base = runCatching { Color(android.graphics.Color.parseColor(storedColor)) }.getOrDefault(Color(0xFFC1E8DE))
    if (LocalBackgroundActive.current) {
        // Imported courses may contain translucent ARGB colors. Give their text a reliable
        // backing over photos, while leaving the stored color and ordinary rendering intact.
        val opaque = base.compositeOver(MaterialTheme.colorScheme.surface)
        return opaque.copy(alpha = cardAlpha) to Color(contrastingTextColor(opaque.toArgb()))
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
    onBlurChanging: (Float) -> Unit = {},
    onBlurChanged: () -> Unit = {},
    onFocusChanged: (BackgroundFocus) -> Unit = {},
) {
    val editable = state.hasImage && !state.busy
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The page itself sits on the photo, so every adjustment below previews live without a thumbnail.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("应用背景", style = MaterialTheme.typography.titleSmall)
                Text(if (state.hasImage) "已设置，调整效果会直接显示在页面背景上" else "选择一张本机图片作为背景",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.hasImage) TextButton(onClick = onRemove, enabled = !state.busy) { Text("移除背景") }
        }
        OutlinedButton(onClick = onChoose, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.hasImage) "更换背景图片" else "选择背景图片")
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在处理背景…", style = MaterialTheme.typography.bodySmall)
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("背景模糊", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${(state.settings.blur * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(state.settings.blur, onBlurChanging, Modifier.fillMaxWidth().testTag("background_blur"),
                enabled = editable, onValueChangeFinished = onBlurChanged)
            Row {
                Text("清晰图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                Text("突出课程", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BackgroundChoice("图片位置", "让人物或主体避开课程区域", listOf(BackgroundFocus.TOP to "偏上",
            BackgroundFocus.CENTER to "居中", BackgroundFocus.BOTTOM to "偏下"), state.settings.focus, editable, onFocusChanged)
        BackgroundSwitch("主题跟随背景", "根据图片自动生成浅色和深色主题", state.settings.imageTheme,
            editable, onImageThemeChanged)
        BackgroundSwitch("课程跟随背景配色", "为课程自动配色，关闭后恢复原有颜色", state.settings.courseTheme,
            editable, onCourseThemeChanged)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("图片仅保存在本机，不会上传；课表备份不包含背景图片。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> BackgroundChoice(title: String, subtitle: String, options: List<Pair<T, String>>, selected: T,
    enabled: Boolean, onChange: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(selected = value == selected, onClick = { onChange(value) }, enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)) { Text(label) }
            }
        }
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
