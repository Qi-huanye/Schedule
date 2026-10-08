package dev.wakeuppure.background

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import dev.wakeuppure.ui.background.BackgroundUiState
import dev.wakeuppure.ui.background.BackgroundViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundViewModelTest {
    private lateinit var app: Application
    private val models = mutableListOf<BackgroundViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("background_settings", 0).edit().clear().commit()
        File(app.filesDir, "backgrounds").deleteRecursively()
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        File(app.filesDir, "backgrounds").deleteRecursively()
        File(app.cacheDir, "background-vm.png").delete()
        File(app.cacheDir, "background-vm-invalid.txt").delete()
    }

    @Test fun importAndIndependentSwitchesSurviveRestart() = runBlocking {
        val vm = model()
        settled(vm)
        vm.importImage(image())
        val imported = settled(vm)
        assertTrue(imported.hasImage)
        assertTrue(imported.settings.imageTheme)
        assertFalse(imported.settings.courseTheme)
        assertNotEquals(imported.colors!!.light, imported.colors.dark)
        assertEquals(6, imported.colors.lightCourses.size)

        vm.setImageTheme(false)
        settled(vm)
        vm.setCourseTheme(true)
        val changed = settled(vm)
        assertSame(imported.bitmap, changed.bitmap)
        assertSame(imported.colors, changed.colors)
        assertFalse(changed.settings.imageTheme)
        assertTrue(changed.settings.courseTheme)

        val restarted = settled(model())
        assertTrue(restarted.hasImage)
        assertEquals(changed.settings, restarted.settings)
        assertEquals(changed.colors!!.light.primary, restarted.colors!!.light.primary)
        assertEquals(changed.colors.dark.primary, restarted.colors.dark.primary)
        assertEquals(changed.colors.lightCourses, restarted.colors.lightCourses)
        assertEquals(changed.colors.darkCourses, restarted.colors.darkCourses)
    }

    @Test fun failedReplacementKeepsCurrentAppearanceAndCanRecover() = runBlocking {
        val vm = model()
        settled(vm)
        vm.importImage(image())
        val imported = settled(vm)
        assertTrue(imported.hasImage)

        val invalid = File(app.cacheDir, "background-vm-invalid.txt").apply { writeText("not an image") }
        vm.importImage(Uri.fromFile(invalid))
        val failed = settled(vm)
        assertNotNull(failed.error)
        assertSame(imported.bitmap, failed.bitmap)
        assertEquals(imported.settings, failed.settings)

        vm.importImage(image())
        assertNull(settled(vm).error)
        assertTrue(vm.state.value.hasImage)
    }

    @Test fun removingBackgroundRestoresDefaultsAndSurvivesRestart() = runBlocking {
        val vm = model()
        settled(vm)
        vm.importImage(image())
        assertTrue(settled(vm).hasImage)
        vm.setCourseTheme(true)
        settled(vm)
        vm.clearBackground()
        val cleared = settled(vm)
        assertFalse(cleared.hasImage)
        assertNull(cleared.bitmap)
        assertNull(cleared.colors)
        assertTrue(cleared.settings.courseTheme)
        assertFalse(settled(model()).hasImage)
    }

    @Test fun corruptPrivateImageDoesNotApplyStaleColorsOnRestart() = runBlocking {
        val vm = model()
        settled(vm)
        vm.importImage(image())
        val imported = settled(vm)
        assertTrue(imported.hasImage)
        File(app.filesDir, "backgrounds/${imported.settings.imageName}").writeText("broken image")
        val restarted = settled(model())
        assertFalse(restarted.hasImage)
        assertNull(restarted.bitmap)
        assertNull(restarted.colors)
        assertNotNull(restarted.error)
    }

    @Test fun pickerResultDuringStartupIsImportedAfterInitialLoading() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val vm = model()
        assertTrue(vm.state.value.busy)
        // Android can restore a pending activity result before the newly created VM has loaded.
        vm.importImage(image())
        withTimeout(15_000) {
            do {
                dispatcher.scheduler.runCurrent()
                delay(10)
            } while (vm.state.value.busy)
        }
        assertTrue(vm.state.value.hasImage)
        assertNull(vm.state.value.error)
    }

    private fun model() = BackgroundViewModel(app).also(models::add)

    private suspend fun settled(vm: BackgroundViewModel): BackgroundUiState =
        withTimeout(15_000) { vm.state.first { !it.busy } }

    private fun image(): Uri {
        val file = File(app.cacheDir, "background-vm.png")
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(42, 144, 109))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
}
