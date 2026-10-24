package dev.wakeuppure.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.wakeuppure.ui.background.BackgroundContainer
import dev.wakeuppure.ui.background.BackgroundUiState
import dev.wakeuppure.ui.background.imageColorScheme
import androidx.compose.runtime.remember
import dev.wakeuppure.domain.model.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PureTheme(mode: String, background: BackgroundUiState? = null, theme: ThemeSettings = ThemeSettings(), content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val imageColors = background?.colors?.takeIf { background.themeReady && background.settings.imageTheme }
    val colors = if (imageColors != null) {
        if (dark) imageColors.dark else imageColors.light
    } else if (theme.source == ThemeSource.SYSTEM && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else remember(dark, theme.preset) { imageColorScheme(ThemePresets.find(theme.preset).seed, dark) }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val base = Typography()
    val typography = base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    MaterialExpressiveTheme(
        colorScheme = colors,
        typography = typography,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
    ) { BackgroundContainer(background, dark, content) }
}
