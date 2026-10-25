package dev.wakeuppure.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import dev.wakeuppure.domain.model.InterfaceSize

private val LocalSystemDensity = staticCompositionLocalOf<Density?> { null }

/** Nested previews use the system baseline, so the chosen size is never applied twice. */
@Composable
internal fun InterfaceScale(percent: Int, content: @Composable () -> Unit) {
    val system = LocalSystemDensity.current ?: LocalDensity.current
    val adjusted = remember(system, percent) {
        val scale = InterfaceSize.clamp(percent) / 100f
        if (scale == 1f) system else object : Density {
            override val density = system.density * scale
            override val fontScale = system.fontScale
            // Retain Android's nonlinear font scaling as well as its font-size preference.
            override fun TextUnit.toDp(): Dp = with(system) { this@toDp.toDp() }
            override fun Dp.toSp(): TextUnit = with(system) { this@toSp.toSp() }
        }
    }
    CompositionLocalProvider(LocalSystemDensity provides system, LocalDensity provides adjusted, content = content)
}
