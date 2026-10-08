package dev.wakeuppure.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.wakeuppure.ui.background.BackgroundContainer
import dev.wakeuppure.ui.background.BackgroundUiState

@Composable
fun PureTheme(mode: String, background: BackgroundUiState? = null, content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val imageColors = background?.colors?.takeIf { background.hasImage && background.settings.imageTheme }
    val colors = if (imageColors != null) {
        if (dark) imageColors.dark else imageColors.light
    } else if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(primary = Color(0xFF89D4C8), secondary = Color(0xFFE6C176), background = Color(0xFF151918))
    else lightColorScheme(primary = Color(0xFF16695F), secondary = Color(0xFF806127), background = Color(0xFFF7FAF8))
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors) { BackgroundContainer(background, dark, content) }
}
