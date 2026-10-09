package dev.wakeuppure.ui.background

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.ColorScheme
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wakeuppure.data.background.BackgroundRepository
import dev.wakeuppure.domain.model.BackgroundFocus
import dev.wakeuppure.domain.model.BackgroundSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackgroundColors(
    val light: ColorScheme,
    val dark: ColorScheme,
    val lightCourses: List<Int>,
    val darkCourses: List<Int>,
)

data class BackgroundUiState(
    val settings: BackgroundSettings = BackgroundSettings(),
    val bitmap: Bitmap? = null,
    val colors: BackgroundColors? = null,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val hasImage: Boolean get() = settings.hasImage && bitmap != null && colors != null
}

class BackgroundViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackgroundRepository(application)
    private val mutableState = MutableStateFlow(BackgroundUiState())
    val state = mutableState.asStateFlow()
    private var pendingImage: Uri? = null
    // Image operations and quick saves run one at a time in call order: viewModelScope starts each
    // coroutine on the main thread right away, so the fair mutex queues them as they were called.
    private val operations = Mutex()
    private var pendingSaves = 0

    init {
        perform("背景加载失败，请重新选择图片。") {
            appearance(repository.restore())
        }
    }

    fun importImage(uri: Uri) {
        if (state.value.busy) {
            // A restored picker result can arrive while a new ViewModel is still loading.
            pendingImage = uri
            return
        }
        perform("无法读取这张图片，请选择其他图片（最大 32 MB）。") {
            appearance(repository.importImage(uri))
        }
    }

    fun setImageTheme(enabled: Boolean) = setOptions(enabled, state.value.settings.courseTheme)
    fun setCourseTheme(enabled: Boolean) = setOptions(state.value.settings.imageTheme, enabled)

    /** Live slider feedback: updates the screen only; [commitBlur] persists the final value. */
    fun previewBlur(blur: Float) {
        if (state.value.busy) return
        mutableState.update { it.copy(settings = it.settings.copy(blur = blur.coerceIn(0f, 1f))) }
    }
    fun commitBlur() = save({ it }) { repository.setLayout(it.blur, it.focus) }
    fun setFocus(focus: BackgroundFocus) = save({ it.copy(focus = focus) }) { repository.setLayout(it.blur, it.focus) }

    private fun setOptions(imageTheme: Boolean, courseTheme: Boolean) =
        save({ it.copy(imageTheme = imageTheme, courseTheme = courseTheme) }) { repository.setOptions(it.imageTheme, it.courseTheme) }

    /**
     * Quick settings show at once and are stored in the background. They never set [BackgroundUiState.busy]:
     * a few milliseconds of busy would insert the progress bar and disable the controls, making the
     * section jump right after the blur slider is released.
     */
    private fun save(change: (BackgroundSettings) -> BackgroundSettings, persist: suspend (BackgroundSettings) -> BackgroundSettings) {
        mutableState.update { it.copy(settings = change(it.settings), error = null) }
        val target = state.value.settings
        pendingSaves++
        viewModelScope.launch {
            try {
                val saved = operations.withLock { persist(target) }
                // Once every queued change is stored, the stored settings are what the screen should
                // show; adopting them also restores a change that an image operation published over.
                if (pendingSaves == 1) mutableState.update { it.copy(settings = saved) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val stored = withContext(Dispatchers.IO) { repository.load() }
                mutableState.update { it.copy(settings = stored, error = "外观设置保存失败，请重试。") }
            } finally {
                pendingSaves--
            }
        }
    }

    /** Suspends until every queued image operation and save has finished. */
    internal suspend fun awaitIdle() = operations.withLock {}

    fun clearBackground() {
        perform("背景移除失败，请重试。") { BackgroundUiState(settings = repository.clear()) }
    }

    private suspend fun appearance(settings: BackgroundSettings): BackgroundUiState {
        if (!settings.hasImage) return BackgroundUiState(settings = settings)
        val bitmap = repository.readBitmap(settings)
            ?: return BackgroundUiState(settings = settings.copy(imageName = null, colors = null), error = "背景图片无法读取，请重新选择。")
        val colors = withContext(Dispatchers.Default) {
            val source = repository.withClusters(requireNotNull(settings.colors), bitmap)
            BackgroundColors(imageColorScheme(source.seed, false), imageColorScheme(source.seed, true),
                imageCourseColors(source, false), imageCourseColors(source, true))
        }
        return BackgroundUiState(settings, bitmap, colors)
    }

    private fun perform(failureMessage: String, action: suspend () -> BackgroundUiState) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val next = try {
                operations.withLock { action() }.copy(busy = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value.copy(busy = false, error = failureMessage)
            }
            // Publish exactly once: a later write here could clear the busy flag of an action
            // that started as soon as this one became idle.
            mutableState.value = next
            pendingImage?.let { uri ->
                pendingImage = null
                importImage(uri)
            }
        }
    }
}
