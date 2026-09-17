package dev.wakeuppure.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun PureTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(primary = Color(0xFF89D4C8), secondary = Color(0xFFE6C176), background = Color(0xFF151918))
    else lightColorScheme(primary = Color(0xFF16695F), secondary = Color(0xFF806127), background = Color(0xFFF7FAF8))
    MaterialTheme(colorScheme = colors, content = content)
}
