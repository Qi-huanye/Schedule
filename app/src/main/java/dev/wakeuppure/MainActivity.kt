package dev.wakeuppure

import android.os.Bundle
import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.wakeuppure.ui.PureViewModel
import dev.wakeuppure.ui.background.BackgroundViewModel

class MainActivity : ComponentActivity() {
    private val schedules: PureViewModel by viewModels()
    private val backgrounds: BackgroundViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Created here rather than on the first composition, so both start loading while the system
        // is still showing the starting window.
        val scheduleModel = schedules
        val backgroundModel = backgrounds
        // Seed the exact edge-to-edge window size before loading pixels. The measured root keeps
        // it up to date afterward, including multi-window changes and older Android versions.
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.currentWindowMetrics.bounds
            backgroundModel.setViewport(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
            backgroundModel.setViewport(metrics.widthPixels, metrics.heightPixels)
        }
        var contentReady = false
        val started = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            // The starting window is the app's own splash, so holding it hides the empty frame that
            // would otherwise appear while the snapshot and Room are read. The deadline keeps a
            // wedged database from trapping anyone on it.
            SystemClock.uptimeMillis() - started < SPLASH_LIMIT_MS &&
                !contentReady
        }
        splash.setOnExitAnimationListener { provider ->
            // No second splash or minimum delay. Respect the system's animator setting.
            if (!ValueAnimator.areAnimatorsEnabled()) provider.remove()
            else provider.view.animate().alpha(0f).scaleX(1.05f).scaleY(1.05f)
                .setDuration(200L).setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction { provider.remove() }.start()
        }
        setContent {
            dev.wakeuppure.ui.PureRoot(vm = scheduleModel, backgrounds = backgroundModel,
                onContentReady = { contentReady = true }, openToday = intent.getBooleanExtra("open_today", false),
                initialTodoId = intent.getLongExtra("todo_id", 0).takeIf { it > 0 },
                initialScheduleId = intent.getLongExtra("schedule_id", 0).takeIf { it > 0 })
        }
    }

    private companion object { const val SPLASH_LIMIT_MS = 2_000L }
}
