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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    fun commitBlur() = setLayout(state.value.settings.blur, state.value.settings.focus)
    fun setFocus(focus: BackgroundFocus) = setLayout(state.value.settings.blur, focus)

    private fun setLayout(blur: Float, focus: BackgroundFocus) {
        perform("外观设置保存失败，请重试。") {
            state.value.copy(settings = repository.setLayout(blur, focus), error = null)
        }
    }

    private fun setOptions(imageTheme: Boolean, courseTheme: Boolean) {
        perform("外观设置保存失败，请重试。") {
            state.value.copy(settings = repository.setOptions(imageTheme, courseTheme), error = null)
        }
    }

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
            try {
                mutableState.value = action().copy(busy = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(error = failureMessage) }
            } finally {
                mutableState.update { it.copy(busy = false) }
                if (currentCoroutineContext().isActive) {
                    pendingImage?.let { uri ->
                        pendingImage = null
                        importImage(uri)
                    }
                }
            }
        }
    }
}
