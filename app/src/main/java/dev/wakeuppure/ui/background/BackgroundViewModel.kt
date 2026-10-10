package dev.wakeuppure.ui.background

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.material3.ColorScheme
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wakeuppure.data.background.BackgroundRepository
import dev.wakeuppure.domain.model.BackgroundFocus
import dev.wakeuppure.domain.model.BackgroundSettings
import dev.wakeuppure.domain.model.ImageColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
    /**
     * The palette is known, so the theme and every scrim already match the photo. The photo itself
     * may still be loading: it is stored separately and only needs to be drawn.
     */
    val themeReady: Boolean get() = settings.hasImage && colors != null
    val hasImage: Boolean get() = themeReady && bitmap != null
}

class BackgroundViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackgroundRepository(application)
    private var viewport = repository.defaultDisplaySize()
    private data class DisplayRequest(val imageName: String?, val focus: BackgroundFocus, val size: Size)
    private var displayed: DisplayRequest? = null
    private val mutableState = MutableStateFlow(BackgroundUiState())
    val state = mutableState.asStateFlow()
    private var pendingImage: Uri? = null
    // Image operations and quick saves run one at a time in call order: viewModelScope starts each
    // coroutine on the main thread right away, so the fair mutex queues them as they were called.
    private val operations = Mutex()
    private var pendingSaves = 0
    private val mutableReady = MutableStateFlow(false)
    // The first load decides the opening screen; later imports must not move the starting window.
    private var initialLoad = true
    /** True once the opening screen is fully prepared, photo included. */
    val ready: StateFlow<Boolean> = mutableReady.asStateFlow()

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
            appearance(repository.importImage(uri, viewport))
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

    /** Also handles rotation and multi-window resizing on an already running ViewModel. */
    fun setViewport(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val size = Size(width, height)
        if (size == viewport) return
        viewport = size
        viewModelScope.launch {
            try {
                operations.withLock { refreshDisplay() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(error = "背景加载失败，请重新选择图片。") }
            }
        }
    }

    private fun setOptions(imageTheme: Boolean, courseTheme: Boolean) =
        save({ it.copy(imageTheme = imageTheme, courseTheme = courseTheme) }) { repository.setOptions(it.imageTheme, it.courseTheme) }

    /**
     * Quick settings show at once and are stored in the background. They never set [BackgroundUiState.busy]:
     * a few milliseconds of busy would insert the progress bar and disable the controls, making the
     * section jump right after the blur slider is released.
     */
    private fun save(
        change: (BackgroundSettings) -> BackgroundSettings,
        persist: suspend (BackgroundSettings) -> BackgroundSettings,
    ) {
        mutableState.update { it.copy(settings = change(it.settings), error = null) }
        val target = state.value.settings
        pendingSaves++
        viewModelScope.launch {
            try {
                operations.withLock {
                    val saved = persist(target)
                    // Once every queued change is stored, reconcile the optimistic controls with disk.
                    if (pendingSaves == 1) {
                        mutableState.update { it.copy(settings = saved) }
                        refreshDisplay()
                    }
                }
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

    /** Old focus/size requests may finish after newer input; only publish matching pixels. */
    private suspend fun refreshDisplay() {
        val settings = state.value.settings
        if (!settings.hasImage) return
        val size = viewport
        val request = DisplayRequest(settings.imageName, settings.focus, size)
        if (request == displayed) return
        val bitmap = repository.readDisplayBitmap(settings, size) ?: return
        val current = state.value.settings
        if (size == viewport && settings.imageName == current.imageName && settings.focus == current.focus) {
            displayed = request
            mutableState.update { it.copy(bitmap = bitmap) }
        } else {
            bitmap.recycle()
        }
    }

    /** Suspends until every queued image operation and save has finished. */
    internal suspend fun awaitIdle() = operations.withLock {}

    fun clearBackground() {
        perform("背景移除失败，请重试。") { BackgroundUiState(settings = repository.clear()) }
    }

    /** Prepare palette and pixels concurrently; publish only a matching, complete appearance. */
    private suspend fun appearance(settings: BackgroundSettings): BackgroundUiState = coroutineScope {
        if (!settings.hasImage) return@coroutineScope BackgroundUiState(settings = settings)
        // restore() migrates legacy clusters from the uncropped source once; this record is enough
        // even for grayscale photos whose correctly computed clusters are empty.
        val stored = requireNotNull(settings.colors)
        val colors = async(Dispatchers.Default) { palette(stored) }
        var result: BackgroundUiState? = null
        while (result == null) {
            val request = DisplayRequest(settings.imageName, settings.focus, viewport)
            val bitmap = repository.readDisplayBitmap(settings, request.size)
                ?: return@coroutineScope BackgroundUiState(settings = settings.copy(imageName = null, colors = null), error = "背景图片无法读取，请重新选择。")
            val palette = colors.await()
            // Awaiting either worker can allow a viewport update; check after both have finished.
            if (request.size != viewport) {
                bitmap.recycle()
                continue
            }
            displayed = request
            result = BackgroundUiState(settings, bitmap, palette)
        }
        result
    }

    private fun palette(source: ImageColors): BackgroundColors = BackgroundColors(
        imageColorScheme(source.seed, false), imageColorScheme(source.seed, true),
        imageCourseColors(source, false), imageCourseColors(source, true))


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
            if (initialLoad) {
                initialLoad = false
                mutableReady.value = true
            }
            pendingImage?.let { uri ->
                pendingImage = null
                importImage(uri)
            }
        }
    }
}
