package dev.wakeuppure.ui

import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import dev.wakeuppure.ui.background.BackgroundUiState
import dev.wakeuppure.ui.background.BackgroundViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class StartupState(
    val schedules: PreparedScheduleState,
    val appearance: String,
    val background: BackgroundUiState,
)

/** No partial initial state reaches the expensive navigation/timetable composition. */
internal fun startupState(vm: PureViewModel, backgrounds: BackgroundViewModel) = combine(
    vm.screenState, vm.appearance, backgrounds.state, backgrounds.ready,
) { schedules, appearance, background, backgroundReady ->
    if (schedules == null || !backgroundReady) null else StartupState(schedules, appearance, background)
}.distinctUntilChanged()

/** Post after an actual draw traversal, not just after composition or the next frame callback. */
@Composable
internal fun AfterFirstDraw(onDrawn: () -> Unit) {
    val view = LocalView.current
    val callback by rememberUpdatedState(onDrawn)
    DisposableEffect(view) {
        var active = true
        var posted = false
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnDrawListener {
            if (!posted) {
                posted = true
                view.post { if (active) callback() }
            }
        }
        observer.addOnDrawListener(listener)
        onDispose {
            active = false
            if (observer.isAlive) observer.removeOnDrawListener(listener)
            else view.viewTreeObserver.removeOnDrawListener(listener)
        }
    }
}
